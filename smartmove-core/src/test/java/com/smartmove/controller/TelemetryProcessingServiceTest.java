package com.smartmove.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartmove.audit.AuditLogService;
import com.smartmove.domain.City;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleState;
import com.smartmove.domain.VehicleType;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.zones.ZoneService;

class TelemetryProcessingServiceTest {

    private final AuditLogService auditLog = mock(AuditLogService.class);
    private final ZoneService zoneService = mock(ZoneService.class);

    @Test
    void theftMovementWithoutRentalTriggersEmergencyLock() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v1", VehicleType.E_SCOOTER, City.LONDON);
        vehicle.setState(VehicleState.AVAILABLE);
        vehicle.setRentalActive(false);

        TelemetryData telemetry = new TelemetryData("v1", 0.0, 0.0, 50, 20.0);
        telemetry.setMovementDetected(true);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.EMERGENCY_LOCK, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
        assertEquals(telemetry, vehicle.getTelemetry());
        verify(auditLog).append(eq("THEFT_ALARM"), contains("vehicleId=v1"));
    }

    @Test
    void faultSetsMaintenanceWhenNotEmergencyLocked() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v2", VehicleType.E_SCOOTER, City.LONDON);
        vehicle.setState(VehicleState.AVAILABLE);

        TelemetryData telemetry = new TelemetryData("v2", 0.0, 0.0, 50, 20.0);
        telemetry.setFault(true);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.MAINTENANCE, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
        verify(auditLog).append(eq("FAULT_DETECTED"), contains("vehicleId=v2"));
    }

    @Test
    void overheatTriggersEmergencyLock() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v3", VehicleType.E_SCOOTER, City.LONDON);
        vehicle.setState(VehicleState.IN_USE);
        vehicle.setRentalActive(true);

        TelemetryData telemetry = new TelemetryData("v3", 0.0, 0.0, 50, 70.0);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.EMERGENCY_LOCK, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
        verify(auditLog).append(eq("OVERHEAT_LOCK"), contains("vehicleId=v3"));
    }

    @Test
    void lowBatteryDuringTripTriggersMaintenance() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v4", VehicleType.E_SCOOTER, City.LONDON);
        vehicle.setState(VehicleState.IN_USE);
        vehicle.setRentalActive(true);

        TelemetryData telemetry = new TelemetryData("v4", 0.0, 0.0, 3, 20.0);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.MAINTENANCE, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
        verify(auditLog).append(eq("EMERGENCY_TERMINATION"), contains("vehicleId=v4"));
    }

    @Test
    void romeScooterInRestrictedZoneTriggersZoneViolationAndEmergencyLock() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v5", VehicleType.E_SCOOTER, City.ROME);
        vehicle.setState(VehicleState.IN_USE);
        vehicle.setRentalActive(true);

        TelemetryData telemetry = new TelemetryData("v5", 41.9, 12.5, 50, 20.0);

        when(zoneService.isRestricted(eq(City.ROME), eq(VehicleType.E_SCOOTER), anyDouble(), anyDouble()))
                .thenReturn(true);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.EMERGENCY_LOCK, vehicle.getState());
        verify(auditLog).append("ZONE_VIOLATION", "vehicleId=v5");
    }

    @Test
    void romeScooterOutsideRestrictedZoneDoesNotLogZoneViolation() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v6", VehicleType.E_SCOOTER, City.ROME);
        vehicle.setState(VehicleState.IN_USE);

        TelemetryData telemetry = new TelemetryData("v6", 41.9, 12.5, 50, 20.0);

        when(zoneService.isRestricted(eq(City.ROME), eq(VehicleType.E_SCOOTER), anyDouble(), anyDouble()))
                .thenReturn(false);

        service.applyTelemetryRules(vehicle, telemetry);

        assertEquals(VehicleState.IN_USE, vehicle.getState());
        verify(auditLog, never()).append(eq("ZONE_VIOLATION"), anyString());
    }

    @Test
    void nonRomeOrNonScooterDoesNotCheckRestrictedZone() {
        TelemetryProcessingService service = new TelemetryProcessingService(auditLog, zoneService);

        Vehicle vehicle = new Vehicle("v7", VehicleType.MOPED, City.LONDON);
        vehicle.setState(VehicleState.IN_USE);

        TelemetryData telemetry = new TelemetryData("v7", 10.0, 20.0, 50, 20.0);

        service.applyTelemetryRules(vehicle, telemetry);

        verify(zoneService, never()).isRestricted(any(), any(), anyDouble(), anyDouble());
    }
}