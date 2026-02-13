package com.smartmove.controller;

import com.smartmove.audit.AuditLogService;
import com.smartmove.domain.City;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleState;
import com.smartmove.domain.VehicleType;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.zones.ZoneService;
import com.smartmove.domain.Payment;
import com.smartmove.storage.PaymentStorage;


import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class SmartMoveCentralController {

    private final VehicleStorage storage;
    private final AuditLogService auditLog;
    private final ZoneService zoneService;

    private final PaymentStorage paymentStorage;

    // Manual concurrency management: per-vehicle locks
    private final Map<String, ReentrantLock> vehicleLocks = new ConcurrentHashMap<>();

    // Telemetry background processing
    private final BlockingQueue<TelemetryData> telemetryQueue = new LinkedBlockingQueue<>();
    private final ExecutorService telemetryWorker = Executors.newSingleThreadExecutor();
    

    public SmartMoveCentralController(VehicleStorage storage, AuditLogService auditLog, ZoneService zoneService, PaymentStorage paymentStorage) {
        this.storage = storage;
        this.auditLog = auditLog;
        this.zoneService = zoneService;
        this.paymentStorage = paymentStorage;
        telemetryWorker.submit(this::telemetryLoop);
    }

    public void shutdown() {
        telemetryWorker.shutdownNow();
    }

    public void registerVehicle(Vehicle v) {
        if (v == null || v.getId() == null || v.getId().isBlank()) {
            throw new IllegalArgumentException("Vehicle/id cannot be null");
        }
        ReentrantLock lock = lockFor(v.getId());
        lock.lock();
        try {
            // Ensure initial state
            if (v.getState() == null) v.setState(VehicleState.AVAILABLE);

            // Persist + audit (simple commit order; rollback by restoring snapshot)
            Vehicle snapshot = safeCopy(v);

            try {
                storage.save(v);
                auditLog.append("VEHICLE_REGISTERED", "vehicleId=" + v.getId() + ", type=" + v.getType());
            } catch (Exception ex) {
                // rollback storage to snapshot
                try { storage.save(snapshot); } catch (Exception ignored) {}
                throw new RuntimeException("Failed to register vehicle; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public Optional<Vehicle> getVehicle(String id) {
        return storage.findById(id);
    }

    public void reserveVehicle(String vehicleId, City city) {
        changeState(vehicleId, VehicleState.RESERVED, city, "reserve");
    }

    public void startRental(String vehicleId, City city) {
        ReentrantLock lock = lockFor(vehicleId);
        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
            Vehicle snapshot = safeCopy(v);

            // State machine validation
            validateTransition(v.getState(), VehicleState.IN_USE);

            // City-specific rule: Milan helmet check for Mopeds
            if (city == City.MILAN && v.getType() == VehicleType.MOPED) {
                boolean helmetPresent = v.getTelemetry() != null && v.getTelemetry().isHelmetPresent();
                if (!helmetPresent) {
                    throw new IllegalStateException("Milan rule: Helmet not detected, cannot unlock moped");
                }
            }

            v.setCity(city);
            v.setState(VehicleState.IN_USE);
            v.setRentalActive(true);

            try {
                storage.save(v);
                auditLog.append("RENTAL_STARTED", "vehicleId=" + vehicleId + ", city=" + city);
            } catch (Exception ex) {
                try { storage.save(snapshot); } catch (Exception ignored) {}
                throw new RuntimeException("Failed to start rental; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public void endRental(String vehicleId) {
        ReentrantLock lock = lockFor(vehicleId);
        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
            Vehicle snapshot = safeCopy(v);

            if (v.getState() != VehicleState.IN_USE) {
                throw new IllegalStateException("Vehicle must be IN_USE to end rental");
            }

            // City-specific rule: London congestion charge (hook)
            double baseFare = 10.0; // simple fixed fare for lab
            double congestion = (v.getCity() == City.LONDON) ? 5.0 : 0.0;

            Payment p = new Payment(vehicleId, v.getCity(), baseFare, congestion);
            paymentStorage.save(p);
            auditLog.append("PAYMENT",
                    "paymentId=" + p.getId() + ", vehicleId=" + vehicleId + ", city=" + v.getCity()
                            + ", base=" + baseFare + ", congestion=" + congestion + ", total=" + p.getTotal());


            v.setRentalActive(false);
            v.setState(VehicleState.AVAILABLE);

            try {
                storage.save(v);
                auditLog.append("RENTAL_ENDED", "vehicleId=" + vehicleId + ", city=" + v.getCity());
            } catch (Exception ex) {
                try { storage.save(snapshot); } catch (Exception ignored) {}
                throw new RuntimeException("Failed to end rental; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendTelemetry(TelemetryData t) {
        if (t == null || t.getVehicleId() == null || t.getVehicleId().isBlank()) {
            throw new IllegalArgumentException("Telemetry/vehicleId cannot be null");
        }
        telemetryQueue.offer(t);
    }

    // ---- Telemetry worker ----

    private void telemetryLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TelemetryData t = telemetryQueue.take();
                handleTelemetry(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                // keep processing other telemetry
            }
        }
    }

    /**
     * Must be safe against clashes with active rental transactions:
     * uses per-vehicle primitive lock.
     */
    public void handleTelemetry(TelemetryData t) {
        String vehicleId = t.getVehicleId();
        ReentrantLock lock = lockFor(vehicleId);

        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId).orElse(null);
            if (v == null) return;

            Vehicle snapshot = safeCopy(v);

            // update telemetry
            v.setTelemetry(t);

            // Theft alarm: moved without active rental => emergency lock
            if (t.isMovementDetected() && !v.isRentalActive()) {
                v.setState(VehicleState.EMERGENCY_LOCK);
                v.setRentalActive(false);
                auditLog.append("THEFT_ALARM",
                        "vehicleId=" + vehicleId + ", movementDetected=true, rentalActive=false");
            }

            // Telemetry fault => Maintenance (only if not already emergency locked)
            if (t.isFault() && v.getState() != VehicleState.EMERGENCY_LOCK) {
                v.setState(VehicleState.MAINTENANCE);
                v.setRentalActive(false);
                auditLog.append("FAULT_DETECTED",
                        "vehicleId=" + vehicleId + ", state->MAINTENANCE");
            }

            // Required interventions:
            // 1) Overheat > 60°C => emergency lock
            // Overheat > 60°C => emergency lock + terminate rental
            if (t.getTemperatureC() > 60) {
                v.setRentalActive(false);
                v.setState(VehicleState.EMERGENCY_LOCK);
                auditLog.append("OVERHEAT_LOCK",
                        "vehicleId=" + vehicleId + ", temp=" + t.getTemperatureC());
            }


            // 2) Battery < 5% during trip => maintenance (or emergency terminate)
            // Battery < 5% during trip => emergency terminate rental + maintenance
            if (t.getBatteryPercent() < 5 && v.getState() == VehicleState.IN_USE) {
                v.setRentalActive(false);
                v.setState(VehicleState.MAINTENANCE);
                auditLog.append("EMERGENCY_TERMINATION",
                        "vehicleId=" + vehicleId + ", reason=LOW_BATTERY, batt=" + t.getBatteryPercent());
            }


            // 3) Rome scooter restricted zones
            if (v.getCity() == City.ROME && v.getType() == VehicleType.E_SCOOTER) {
                boolean restricted = zoneService.isRestricted(
                        City.ROME,
                        v.getType(),
                        t.getLatitude(),
                        t.getLongitude()
                );
                if (restricted) {
                    v.setState(VehicleState.EMERGENCY_LOCK);
                    // optional: audit reason
                    // auditLog.append("ZONE_VIOLATION", "vehicleId=" + vehicleId);
                }
            }

            try {
                storage.save(v);
                auditLog.append("TELEMETRY", "vehicleId=" + vehicleId + ", batt=" + t.getBatteryPercent() + ", temp=" + t.getTemperatureC());
            } catch (Exception ex) {
                try { storage.save(snapshot); } catch (Exception ignored) {}
                throw new RuntimeException("Telemetry write failed; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    // ---- State change helper ----

    private void changeState(String vehicleId, VehicleState to, City city, String reason) {
        ReentrantLock lock = lockFor(vehicleId);
        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
            Vehicle snapshot = safeCopy(v);

            validateTransition(v.getState(), to);

            v.setCity(city);
            v.setState(to);

            try {
                storage.save(v);
                auditLog.append("STATE_CHANGE",
                        "vehicleId=" + vehicleId + ", " + snapshot.getState() + "->" + to + ", reason=" + reason);
            } catch (Exception ex) {
                try { storage.save(snapshot); } catch (Exception ignored) {}
                throw new RuntimeException("State change failed; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    private void validateTransition(VehicleState from, VehicleState to) {
        // Expand these rules to fully match your report
        if (to == VehicleState.EMERGENCY_LOCK) return; // can always lock

        switch (from) {
            case AVAILABLE -> {
                if (to != VehicleState.RESERVED && to != VehicleState.RELOCATING) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            case RESERVED -> {
                if (to != VehicleState.IN_USE && to != VehicleState.AVAILABLE) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            case IN_USE -> {
                if (to != VehicleState.AVAILABLE && to != VehicleState.MAINTENANCE && to != VehicleState.EMERGENCY_LOCK) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            case MAINTENANCE -> {
                if (to != VehicleState.AVAILABLE) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            case RELOCATING -> {
                if (to != VehicleState.AVAILABLE) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            case EMERGENCY_LOCK -> {
                if (to != VehicleState.MAINTENANCE) {
                    throw new IllegalStateException("Invalid transition " + from + " -> " + to);
                }
            }
            default -> throw new IllegalStateException("Unknown state: " + from);
        }
    }

    private ReentrantLock lockFor(String vehicleId) {
        return vehicleLocks.computeIfAbsent(vehicleId, id -> new ReentrantLock());
    }

    private Vehicle safeCopy(Vehicle v) {
        // If your Vehicle already has copy(), call it.
        // Otherwise implement copy() in Vehicle and replace this.
        return v.copy();
    }
}
