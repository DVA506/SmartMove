package com.smartmove.controller;

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

import java.util.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SmartMoveCentralController {

    private static final String VEHICLE_NOT_FOUND = "Vehicle not found";
    private static final String VEHICLE_ID_PREFIX = "vehicleId=";
    private static final String CITY_PREFIX = ", city=";

    private final VehicleStorage storage;
    private final AuditLogService auditLog;
    private final PaymentStorage paymentStorage;

    private final VehicleStateValidator stateValidator;
    private final TelemetryProcessingService telemetryProcessingService;

    private final Map<String, ReentrantLock> vehicleLocks = new ConcurrentHashMap<>();
    private final BlockingQueue<TelemetryData> telemetryQueue = new LinkedBlockingQueue<>();
    private final ExecutorService telemetryWorker = Executors.newSingleThreadExecutor();

    private static final Logger logger = Logger.getLogger(SmartMoveCentralController.class.getName());

    public SmartMoveCentralController(
            VehicleStorage storage,
            AuditLogService auditLog,
            ZoneService zoneService,
            PaymentStorage paymentStorage
    ) {
        this.storage = storage;
        this.auditLog = auditLog;
        this.paymentStorage = paymentStorage;
        this.stateValidator = new VehicleStateValidator();
        this.telemetryProcessingService = new TelemetryProcessingService(auditLog, zoneService);
        telemetryWorker.submit(this::telemetryLoop);
    }

    public void shutdown() {
        telemetryWorker.shutdownNow();
    }

    public void registerVehicle(Vehicle vehicle) {
        validateVehicle(vehicle);

        ReentrantLock lock = lockFor(vehicle.getId());
        lock.lock();
        try {
            if (vehicle.getState() == null) {
                vehicle.setState(VehicleState.AVAILABLE);
            }

            Vehicle snapshot = safeCopy(vehicle);

            try {
                storage.save(vehicle);
                auditLog.append(
                        "VEHICLE_REGISTERED",
                        VEHICLE_ID_PREFIX + vehicle.getId() + ", type=" + vehicle.getType()
                );
            } catch (Exception ex) {
                rollbackToSnapshot(snapshot);
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
            Vehicle vehicle = getVehicleOrThrow(vehicleId);
            Vehicle snapshot = safeCopy(vehicle);

            stateValidator.validateTransition(vehicle.getState(), VehicleState.IN_USE);
            validateMilanMopedHelmetRule(city, vehicle);

            vehicle.setCity(city);
            vehicle.setState(VehicleState.IN_USE);
            vehicle.setRentalActive(true);

            try {
                storage.save(vehicle);
                auditLog.append("RENTAL_STARTED", VEHICLE_ID_PREFIX + vehicleId + CITY_PREFIX + city);
            } catch (Exception ex) {
                rollbackToSnapshot(snapshot);
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
            Vehicle vehicle = getVehicleOrThrow(vehicleId);
            Vehicle snapshot = safeCopy(vehicle);

            if (vehicle.getState() != VehicleState.IN_USE) {
                throw new IllegalStateException("Vehicle must be IN_USE to end rental");
            }

            double baseFare = 10.0;
            double congestion = vehicle.getCity() == City.LONDON ? 5.0 : 0.0;

            savePayment(vehicleId, vehicle, baseFare, congestion);

            vehicle.setRentalActive(false);
            vehicle.setState(VehicleState.AVAILABLE);

            try {
                storage.save(vehicle);
                auditLog.append("RENTAL_ENDED", VEHICLE_ID_PREFIX + vehicleId + CITY_PREFIX + vehicle.getCity());
            } catch (Exception ex) {
                rollbackToSnapshot(snapshot);
                throw new SmartMoveOperationException("Failed to end rental; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendTelemetry(TelemetryData telemetry) {
        if (telemetry == null || telemetry.getVehicleId() == null || telemetry.getVehicleId().isBlank()) {
            throw new IllegalArgumentException("Telemetry/vehicleId cannot be null");
        }

        boolean added = false;

        // Tactic 1 & 2: Check return value and retry/block until inserted
        while (!added) {
            try {
                // Try to insert into the queue with 1 second timeout
                added = telemetryQueue.offer(telemetry, 1, TimeUnit.SECONDS);

                if (!added) {
                    // Queue is temporarily full → log and retry
                    logger.warning("Telemetry queue full for vehicle: " + telemetry.getVehicleId() + ", retrying...");
                    auditLog.append("TELEMETRY_RETRY", VEHICLE_ID_PREFIX + telemetry.getVehicleId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.severe("Failed to enqueue telemetry for vehicle: " + telemetry.getVehicleId() + " due to interruption");
                auditLog.append("TELEMETRY_RETRY", VEHICLE_ID_PREFIX + telemetry.getVehicleId());
                break; // stop retrying if interrupted
            }
        }
    }

    private void telemetryLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TelemetryData telemetry = telemetryQueue.take();
                handleTelemetry(telemetry);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                // keep processing later telemetry items
            }
        }
    }

    public void handleTelemetry(TelemetryData telemetry) {
        String vehicleId = telemetry.getVehicleId();
        ReentrantLock lock = lockFor(vehicleId);

        lock.lock();
        try {
            Vehicle vehicle = storage.findById(vehicleId).orElse(null);
            if (vehicle == null) {
                return;
            }

            Vehicle snapshot = safeCopy(vehicle);
            telemetryProcessingService.applyTelemetryRules(vehicle, telemetry);

            try {
                storage.save(vehicle);
                auditLog.append(
                        "TELEMETRY",
                        VEHICLE_ID_PREFIX + vehicleId
                                + ", batt=" + telemetry.getBatteryPercent()
                                + ", temp=" + telemetry.getTemperatureC()
                );
            } catch (Exception ex) {
                rollbackToSnapshot(snapshot);
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
            Vehicle vehicle = getVehicleOrThrow(vehicleId);
            Vehicle snapshot = safeCopy(vehicle);

            stateValidator.validateTransition(vehicle.getState(), to);

            vehicle.setCity(city);
            vehicle.setState(to);

            try {
                storage.save(vehicle);
                auditLog.append(
                        "STATE_CHANGE",
                        VEHICLE_ID_PREFIX + vehicleId + ", " + snapshot.getState() + "->" + to + ", reason=" + reason
                );
            } catch (Exception ex) {
                rollbackToSnapshot(snapshot);
                throw new SmartMoveOperationException("State change failed; rolled back", ex);
            }
        } finally {
            lock.unlock();
        }
    }

    private void validateVehicle(Vehicle vehicle) {
        if (vehicle == null || vehicle.getId() == null || vehicle.getId().isBlank()) {
            throw new IllegalArgumentException("Vehicle/id cannot be null");
        }
    }

    private Vehicle getVehicleOrThrow(String vehicleId) {
        return storage.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException(VEHICLE_NOT_FOUND));
    }

    private void validateMilanMopedHelmetRule(City city, Vehicle vehicle) {
        if (city == City.MILAN && vehicle.getType() == VehicleType.MOPED) {
            boolean helmetPresent = vehicle.getTelemetry() != null && vehicle.getTelemetry().isHelmetPresent();
            if (!helmetPresent) {
                throw new IllegalStateException("Milan rule: Helmet not detected, cannot unlock moped");
            }
        }
    }

    private void savePayment(String vehicleId, Vehicle vehicle, double baseFare, double congestion) {
        Payment payment = new Payment(vehicleId, vehicle.getCity(), baseFare, congestion);
        paymentStorage.save(payment);
        auditLog.append(
                "PAYMENT",
                "paymentId=" + payment.getId()
                        + ", " + VEHICLE_ID_PREFIX + vehicleId
                        + CITY_PREFIX + vehicle.getCity()
                        + ", base=" + baseFare
                        + ", congestion=" + congestion
                        + ", total=" + payment.getTotal()
        );
    }

    private void rollbackToSnapshot(Vehicle snapshot) {
        try {
            storage.save(snapshot);
        } catch (Exception ignored) {
            // best effort rollback
        }
    }

    private ReentrantLock lockFor(String vehicleId) {
        return vehicleLocks.computeIfAbsent(vehicleId, id -> new ReentrantLock());
    }

    private Vehicle safeCopy(Vehicle vehicle) {
        return vehicle.copy();
    }
}