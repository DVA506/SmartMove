package com.smartmove.controller;

import com.smartmove.audit.AuditLogService;
import com.smartmove.domain.*;
import com.smartmove.storage.PaymentStorage;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.zones.ZoneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartMoveCentralControllerTest {

    private final VehicleStorage vehicleStorage = mock(VehicleStorage.class);
    private final AuditLogService auditLog = mock(AuditLogService.class);
    private final ZoneService zoneService = mock(ZoneService.class);
    private final PaymentStorage paymentStorage = mock(PaymentStorage.class);

    private SmartMoveCentralController controller;

    @AfterEach
    void tearDown() {
        if (controller != null) controller.shutdown();
    }

    @Test
    void registerVehicle_setsDefaultState_andAudits() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle();
        v.setId("v1");
        v.setType(VehicleType.E_SCOOTER);
        v.setCity(City.LONDON);
        v.setState(null);

        controller.registerVehicle(v);

        assertEquals(VehicleState.AVAILABLE, v.getState());
        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("VEHICLE_REGISTERED"), contains("vehicleId=v1"));
    }

    @Test
    void reserveVehicle_changesState() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v2", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE);

        when(vehicleStorage.findById("v2")).thenReturn(Optional.of(v));

        controller.reserveVehicle("v2", City.ROME);

        assertEquals(VehicleState.RESERVED, v.getState());
        assertEquals(City.ROME, v.getCity());

        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("STATE_CHANGE"), contains("AVAILABLE->RESERVED"));
    }

    @Test
    void startRental_success() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v3", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.RESERVED);

        when(vehicleStorage.findById("v3")).thenReturn(Optional.of(v));

        controller.startRental("v3", City.ROME);

        assertEquals(VehicleState.IN_USE, v.getState());
        assertTrue(v.isRentalActive());

        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("RENTAL_STARTED"), contains("vehicleId=v3"));
    }

    @Test
    void endRental_createsPayment_andSetsAvailable() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v4", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.IN_USE);
        v.setRentalActive(true);

        when(vehicleStorage.findById("v4")).thenReturn(Optional.of(v));

        controller.endRental("v4");

        assertEquals(VehicleState.AVAILABLE, v.getState());
        assertFalse(v.isRentalActive());

        verify(paymentStorage).save(any(Payment.class));
        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("RENTAL_ENDED"), contains("vehicleId=v4"));
    }

    @Test
    void handleTelemetry_theftMovement_triggersEmergencyLock() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v5", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE);
        v.setRentalActive(false);

        when(vehicleStorage.findById("v5")).thenReturn(Optional.of(v));

        TelemetryData t = new TelemetryData("v5", 0.0, 0.0, 50, 20.0);
        t.setMovementDetected(true);

        controller.handleTelemetry(t);

        assertEquals(VehicleState.EMERGENCY_LOCK, v.getState());
        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("THEFT_ALARM"), contains("vehicleId=v5"));
    }

    @Test
    void handleTelemetry_romeRestrictedZone_locksVehicle() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v6", VehicleType.E_SCOOTER, City.ROME);
        v.setState(VehicleState.IN_USE);
        v.setRentalActive(true);

        when(vehicleStorage.findById("v6")).thenReturn(Optional.of(v));
        when(zoneService.isRestricted(eq(City.ROME), eq(VehicleType.E_SCOOTER), anyDouble(), anyDouble()))
                .thenReturn(true);

        TelemetryData t = new TelemetryData("v6", 41.9, 12.5, 50, 20.0);

        controller.handleTelemetry(t);

        assertEquals(VehicleState.EMERGENCY_LOCK, v.getState());
        verify(vehicleStorage).save(v);
    }
}
