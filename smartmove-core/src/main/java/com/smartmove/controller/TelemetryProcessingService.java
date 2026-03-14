package com.smartmove.controller;

import com.smartmove.audit.AuditLogService;
import com.smartmove.domain.City;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleState;
import com.smartmove.domain.VehicleType;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.zones.ZoneService;

public class TelemetryProcessingService {

    private static final String VEHICLE_ID_PREFIX = "vehicleId=";

    private final AuditLogService auditLog;
    private final ZoneService zoneService;

    public TelemetryProcessingService(AuditLogService auditLog, ZoneService zoneService) {
        this.auditLog = auditLog;
        this.zoneService = zoneService;
    }

    public void applyTelemetryRules(Vehicle vehicle, TelemetryData telemetry) {
        vehicle.setTelemetry(telemetry);

        if (telemetry.isMovementDetected() && !vehicle.isRentalActive()) {
            vehicle.setState(VehicleState.EMERGENCY_LOCK);
            vehicle.setRentalActive(false);
            auditLog.append(
                    "THEFT_ALARM",
                    VEHICLE_ID_PREFIX + vehicle.getId() + ", movementDetected=true, rentalActive=false"
            );
        }

        if (telemetry.isFault() && vehicle.getState() != VehicleState.EMERGENCY_LOCK) {
            vehicle.setState(VehicleState.MAINTENANCE);
            vehicle.setRentalActive(false);
            auditLog.append(
                    "FAULT_DETECTED",
                    VEHICLE_ID_PREFIX + vehicle.getId() + ", state->MAINTENANCE"
            );
        }

        if (telemetry.getTemperatureC() > 60) {
            vehicle.setRentalActive(false);
            vehicle.setState(VehicleState.EMERGENCY_LOCK);
            auditLog.append(
                    "OVERHEAT_LOCK",
                    VEHICLE_ID_PREFIX + vehicle.getId() + ", temp=" + telemetry.getTemperatureC()
            );
        }

        if (telemetry.getBatteryPercent() < 5 && vehicle.getState() == VehicleState.IN_USE) {
            vehicle.setRentalActive(false);
            vehicle.setState(VehicleState.MAINTENANCE);
            auditLog.append(
                    "EMERGENCY_TERMINATION",
                    VEHICLE_ID_PREFIX + vehicle.getId()
                            + ", reason=LOW_BATTERY, batt=" + telemetry.getBatteryPercent()
            );
        }

        if (vehicle.getCity() == City.ROME && vehicle.getType() == VehicleType.E_SCOOTER) {
            boolean restricted = zoneService.isRestricted(
                    City.ROME,
                    vehicle.getType(),
                    telemetry.getLatitude(),
                    telemetry.getLongitude()
            );
            if (restricted) {
                vehicle.setState(VehicleState.EMERGENCY_LOCK);
                auditLog.append("ZONE_VIOLATION", VEHICLE_ID_PREFIX + vehicle.getId());
            }
        }
    }
}