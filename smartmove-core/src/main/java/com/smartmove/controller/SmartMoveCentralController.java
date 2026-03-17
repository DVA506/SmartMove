package com.smartmove.controller;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.smartmove.audit.AuditLogService;
import com.smartmove.domain.City;
import com.smartmove.domain.Payment;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleState;
import com.smartmove.domain.VehicleType;
import com.smartmove.storage.PaymentStorage;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.zones.ZoneService;

public class SmartMoveCentralController {

    private static final String VEHICLE_NOT_FOUND_ERROR = "Vehicle not found";
    private static final String INVALID_TRANSITION_ERROR = "Invalid transition ";
    private static final double OVERHEAT_TEMPERATURE_C = 60.0;
    private static final int LOW_BATTERY_PERCENT = 5;

    private final VehicleStorage storage;
    private final AuditLogService auditLog;
    private final ZoneService zoneService;
    private final PaymentStorage paymentStorage;

    private final Map<String, ReentrantLock> vehicleLocks = new ConcurrentHashMap<>();
    private final BlockingQueue<TelemetryData> telemetryQueue = new LinkedBlockingQueue<>();
    private final ExecutorService telemetryWorker = Executors.newSingleThreadExecutor();

    public SmartMoveCentralController(
            VehicleStorage storage,
            AuditLogService auditLog,
            ZoneService zoneService,
            PaymentStorage paymentStorage) {
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
            if (v.getState() == null) {
                v.setState(VehicleState.AVAILABLE);
            }

            Vehicle snapshot = safeCopy(v);

            try {
                storage.save(v);
                auditLog.append("VEHICLE_REGISTERED", vehiclePrefix(v.getId()) + ", type=" + v.getType());
            } catch (Exception ex) {
                rollbackVehicle(snapshot, v.getId(), "register");
                throw new SmartMoveOperationException("Failed to register vehicle; rolled back", ex);
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
            Vehicle v = storage.findById(vehicleId)
                    .orElseThrow(() -> new IllegalArgumentException(VEHICLE_NOT_FOUND_ERROR));
            Vehicle snapshot = safeCopy(v);

            validateTransition(v.getState(), VehicleState.IN_USE);

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
                auditLog.append("RENTAL_STARTED", vehiclePrefix(vehicleId) + ", " + cityPrefix(city));
            } catch (Exception ex) {
                rollbackVehicle(snapshot, vehicleId, "startRental");
                throw new SmartMoveOperationException("Failed to start rental; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public void endRental(String vehicleId) {
        ReentrantLock lock = lockFor(vehicleId);
        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId)
                    .orElseThrow(() -> new IllegalArgumentException(VEHICLE_NOT_FOUND_ERROR));
            Vehicle snapshot = safeCopy(v);

            if (v.getState() != VehicleState.IN_USE) {
                throw new IllegalStateException("Vehicle must be IN_USE to end rental");
            }

            double baseFare = 10.0;
            double congestion = (v.getCity() == City.LONDON) ? 5.0 : 0.0;

            Payment p = new Payment(vehicleId, v.getCity(), baseFare, congestion);
            paymentStorage.save(p);
            auditLog.append(
                    "PAYMENT",
                    "paymentId=" + p.getId()
                            + ", " + vehiclePrefix(vehicleId)
                            + ", " + cityPrefix(v.getCity())
                            + ", base=" + baseFare
                            + ", congestion=" + congestion
                            + ", total=" + p.getTotal());

            v.setRentalActive(false);
            v.setState(VehicleState.AVAILABLE);

            try {
                storage.save(v);
                auditLog.append("RENTAL_ENDED", vehiclePrefix(vehicleId) + ", " + cityPrefix(v.getCity()));
            } catch (Exception ex) {
                rollbackVehicle(snapshot, vehicleId, "endRental");
                throw new SmartMoveOperationException("Failed to end rental; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendTelemetry(TelemetryData t) {
        if (t == null || t.getVehicleId() == null || t.getVehicleId().isBlank()) {
            throw new IllegalArgumentException("Telemetry/vehicleId cannot be null");
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                boolean offered = telemetryQueue.offer(t, 1L, TimeUnit.SECONDS);
                if (offered) {
                    return;
                }

                auditLog.append("TELEMETRY_RETRY", vehiclePrefix(t.getVehicleId()) + ", attempt=" + attempt);
            } catch (InterruptedException e) {
                auditLog.append("TELEMETRY_RETRY", vehiclePrefix(t.getVehicleId()) + ", interrupted=true");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void telemetryLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TelemetryData t = telemetryQueue.take();
                handleTelemetry(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                auditLog.append("TELEMETRY_PROCESSING_ERROR", "reason=" + ex.getMessage());
            }
        }
    }

    public void handleTelemetry(TelemetryData t) {
        String vehicleId = t.getVehicleId();
        ReentrantLock lock = lockFor(vehicleId);

        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId).orElse(null);
            if (v == null) {
                return;
            }

            Vehicle snapshot = safeCopy(v);
            v.setTelemetry(t);

            if (t.isMovementDetected() && !v.isRentalActive()) {
                v.setState(VehicleState.EMERGENCY_LOCK);
                v.setRentalActive(false);
                auditLog.append(
                        "THEFT_ALARM",
                        vehiclePrefix(vehicleId) + ", movementDetected=true, rentalActive=false");
            }

            if (t.isFault() && v.getState() != VehicleState.EMERGENCY_LOCK) {
                v.setState(VehicleState.MAINTENANCE);
                v.setRentalActive(false);
                auditLog.append("FAULT_DETECTED", vehiclePrefix(vehicleId) + ", state->MAINTENANCE");
            }

            if (t.getTemperatureC() > OVERHEAT_TEMPERATURE_C) {
                v.setRentalActive(false);
                v.setState(VehicleState.EMERGENCY_LOCK);
                auditLog.append("OVERHEAT_LOCK", vehiclePrefix(vehicleId) + ", temp=" + t.getTemperatureC());
            }

            if (t.getBatteryPercent() < LOW_BATTERY_PERCENT && v.getState() == VehicleState.IN_USE) {
                v.setRentalActive(false);
                v.setState(VehicleState.MAINTENANCE);
                auditLog.append(
                        "EMERGENCY_TERMINATION",
                        vehiclePrefix(vehicleId) + ", reason=LOW_BATTERY, batt=" + t.getBatteryPercent());
            }

            if (v.getCity() == City.ROME && v.getType() == VehicleType.E_SCOOTER) {
                boolean restricted = zoneService.isRestricted(
                        City.ROME,
                        v.getType(),
                        t.getLatitude(),
                        t.getLongitude());
                if (restricted) {
                    v.setState(VehicleState.EMERGENCY_LOCK);
                }
            }

            try {
                storage.save(v);
                auditLog.append(
                        "TELEMETRY",
                        vehiclePrefix(vehicleId) + ", batt=" + t.getBatteryPercent() + ", temp=" + t.getTemperatureC());
            } catch (Exception ex) {
                rollbackVehicle(snapshot, vehicleId, "handleTelemetry");
                throw new SmartMoveOperationException("Telemetry write failed; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    private void changeState(String vehicleId, VehicleState to, City city, String reason) {
        ReentrantLock lock = lockFor(vehicleId);
        lock.lock();
        try {
            Vehicle v = storage.findById(vehicleId)
                    .orElseThrow(() -> new IllegalArgumentException(VEHICLE_NOT_FOUND_ERROR));
            Vehicle snapshot = safeCopy(v);

            validateTransition(v.getState(), to);

            v.setCity(city);
            v.setState(to);

            try {
                storage.save(v);
                auditLog.append(
                        "STATE_CHANGE",
                        vehiclePrefix(vehicleId) + ", " + snapshot.getState() + "->" + to + ", reason=" + reason);
            } catch (Exception ex) {
                rollbackVehicle(snapshot, vehicleId, "changeState");
                throw new SmartMoveOperationException("State change failed; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    private void validateTransition(VehicleState from, VehicleState to) {
        if (to == VehicleState.EMERGENCY_LOCK) {
            return;
        }

        if (!allowedTargets(from).contains(to)) {
            throw new IllegalStateException(INVALID_TRANSITION_ERROR + from + " -> " + to);
        }
    }

    private Set<VehicleState> allowedTargets(VehicleState from) {
        return switch (from) {
            case AVAILABLE -> EnumSet.of(
                    VehicleState.RESERVED,
                    VehicleState.RELOCATING);
            case RESERVED -> EnumSet.of(
                    VehicleState.IN_USE,
                    VehicleState.AVAILABLE);
            case IN_USE -> EnumSet.of(
                    VehicleState.AVAILABLE,
                    VehicleState.MAINTENANCE,
                    VehicleState.EMERGENCY_LOCK);
            case MAINTENANCE, RELOCATING -> EnumSet.of(
                    VehicleState.AVAILABLE);
            case EMERGENCY_LOCK -> EnumSet.of(
                    VehicleState.MAINTENANCE);
        };
    }

    private void rollbackVehicle(Vehicle snapshot, String vehicleId, String operation) {
        try {
            storage.save(snapshot);
            auditLog.append("ROLLBACK_SUCCESS", vehiclePrefix(vehicleId) + ", operation=" + operation);
        } catch (Exception rollbackEx) {
            auditLog.append(
                    "ROLLBACK_FAILED",
                    vehiclePrefix(vehicleId) + ", operation=" + operation + ", reason=" + rollbackEx.getMessage());
        }
    }

    private ReentrantLock lockFor(String vehicleId) {
        return vehicleLocks.computeIfAbsent(vehicleId, id -> new ReentrantLock());
    }

    private Vehicle safeCopy(Vehicle v) {
        return v.copy();
    }

    private static String vehiclePrefix(String vehicleId) {
        return "vehicleId=" + vehicleId;
    }

    private static String cityPrefix(City city) {
        return "city=" + city;
    }
}