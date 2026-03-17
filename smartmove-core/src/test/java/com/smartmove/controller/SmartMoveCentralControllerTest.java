package com.smartmove.controller;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class SmartMoveCentralControllerTest {

    private final VehicleStorage vehicleStorage = mock(VehicleStorage.class);
    private final AuditLogService auditLog = mock(AuditLogService.class);
    private final ZoneService zoneService = mock(ZoneService.class);
    private final PaymentStorage paymentStorage = mock(PaymentStorage.class);

    private SmartMoveCentralController controller;

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    @Test
    void registerVehicleSetsDefaultStateAndAudits() {
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
    void reserveVehicleChangesState() {
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
    void startRentalSuccess() {
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
    void endRentalCreatesPaymentAndSetsAvailable() {
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
    void handleTelemetryTheftMovementTriggersEmergencyLock() {
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
    void handleTelemetryRomeRestrictedZoneLocksVehicle() {
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

    @Test
    void handleTelemetryOverheatTriggersEmergencyLock() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v7", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.IN_USE);
        v.setRentalActive(true);

        when(vehicleStorage.findById("v7")).thenReturn(Optional.of(v));

        TelemetryData t = new TelemetryData("v7", 0.0, 0.0, 50, 70.0);

        controller.handleTelemetry(t);

        assertEquals(VehicleState.EMERGENCY_LOCK, v.getState());
    }

    @Test
    void handleTelemetryLowBatteryTriggersMaintenance() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v8", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.IN_USE);
        v.setRentalActive(true);

        when(vehicleStorage.findById("v8")).thenReturn(Optional.of(v));

        TelemetryData t = new TelemetryData("v8", 0.0, 0.0, 3, 20.0);

        controller.handleTelemetry(t);

        assertEquals(VehicleState.MAINTENANCE, v.getState());
    }

    @Test
    void handleTelemetryFaultSetsMaintenance() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v9", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE);

        when(vehicleStorage.findById("v9")).thenReturn(Optional.of(v));

        TelemetryData t = new TelemetryData("v9", 0.0, 0.0, 50, 20.0);
        t.setFault(true);

        controller.handleTelemetry(t);

        assertEquals(VehicleState.MAINTENANCE, v.getState());
    }

    @Test
    void startRentalVehicleNotFoundThrowsException() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        when(vehicleStorage.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> controller.startRental("missing", City.LONDON));
    }

    @Test
    void startRentalInvalidStateThrowsException() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v10", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE); // invalid for startRental

        when(vehicleStorage.findById("v10")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> controller.startRental("v10", City.LONDON));
    }

    @Test
    void endRentalInvalidStateThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("e1", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE);

        when(vehicleStorage.findById("e1")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> controller.endRental("e1"));
    }

    @Test
    void sendTelemetryInvalidInputThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        assertThrows(IllegalArgumentException.class,
                () -> controller.sendTelemetry(null));
    }

    @Test
    void handleTelemetryVehicleNotFoundDoesNothing() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        when(vehicleStorage.findById("unknown")).thenReturn(Optional.empty());

        TelemetryData t = new TelemetryData("unknown", 0, 0, 50, 20);

        assertDoesNotThrow(() -> controller.handleTelemetry(t));
    }

    @Test
    void reserveInvalidTransitionThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("x1", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.MAINTENANCE); // invalid for reserve

        when(vehicleStorage.findById("x1")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> controller.reserveVehicle("x1", City.LONDON));
    }

    @Test
    void registerVehicleNullThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        assertThrows(IllegalArgumentException.class,
                () -> controller.registerVehicle(null));
    }

    @Test
    void sendTelemetryBlankVehicleIdThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        TelemetryData t = new TelemetryData();
        t.setVehicleId("   ");

        assertThrows(IllegalArgumentException.class,
                () -> controller.sendTelemetry(t));
    }

    @Test
    void getVehicleDelegatesToStorage() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("g1", VehicleType.E_SCOOTER, City.LONDON);
        when(vehicleStorage.findById("g1")).thenReturn(Optional.of(v));

        Optional<Vehicle> result = controller.getVehicle("g1");

        assertTrue(result.isPresent());
        assertEquals("g1", result.get().getId());
        verify(vehicleStorage).findById("g1");
    }

    
}
