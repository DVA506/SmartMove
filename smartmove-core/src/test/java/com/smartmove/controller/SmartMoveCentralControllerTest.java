package com.smartmove.controller;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        // Correct constructor with all parameters
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

    // --- New test for reliability tactics on sendTelemetry ---
    @Test
    void sendTelemetryQueueFullRetriesAndLogs() throws Exception {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        BlockingQueue<TelemetryData> queue = mock(BlockingQueue.class);
        when(queue.offer(any(TelemetryData.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(false) // first attempt fails
                .thenReturn(false) // second attempt fails
                .thenReturn(true); // third attempt succeeds

        java.lang.reflect.Field field = SmartMoveCentralController.class.getDeclaredField("telemetryQueue");
        field.setAccessible(true);
        field.set(controller, queue);

        TelemetryData t = new TelemetryData("v7", 0.0, 0.0, 50, 20.0);

        controller.sendTelemetry(t);

        verify(queue, times(3)).offer(eq(t), eq(1L), eq(TimeUnit.SECONDS));
        verify(auditLog, times(2)).append(eq("TELEMETRY_RETRY"), contains("vehicleId=v7"));
    }

    @Test
    void sendTelemetryQueueInterrupted_logsAndStops() throws Exception {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        BlockingQueue<TelemetryData> queue = mock(BlockingQueue.class);
        when(queue.offer(any(TelemetryData.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException());

        java.lang.reflect.Field field = SmartMoveCentralController.class.getDeclaredField("telemetryQueue");
        field.setAccessible(true);
        field.set(controller, queue);

        TelemetryData t = new TelemetryData("v8", 0.0, 0.0, 50, 20.0);

        controller.sendTelemetry(t);

        verify(queue, times(1)).offer(eq(t), eq(1L), eq(TimeUnit.SECONDS));
        verify(auditLog).append(eq("TELEMETRY_RETRY"), contains("vehicleId=v8"));

        assertTrue(Thread.currentThread().isInterrupted());

        // Clear interrupt flag so it does not affect other tests
        Thread.interrupted();
    }

    @Test
    void sendTelemetryAcceptsValidTelemetry() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        TelemetryData telemetry = new TelemetryData("vehicle-1", 0.0, 0.0, 50, 20.0);

        assertDoesNotThrow(() -> controller.sendTelemetry(telemetry));
    }

    @Test
    void startRentalMilanMopedWithHelmetSucceeds() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("m2", VehicleType.MOPED, City.MILAN);
        v.setState(VehicleState.RESERVED);

        TelemetryData t = new TelemetryData("m2", 0.0, 0.0, 50, 20.0);
        t.setHelmetPresent(true);
        v.setTelemetry(t);

        when(vehicleStorage.findById("m2")).thenReturn(Optional.of(v));

        controller.startRental("m2", City.MILAN);

        assertEquals(VehicleState.IN_USE, v.getState());
        assertTrue(v.isRentalActive());
        verify(vehicleStorage).save(v);
        verify(auditLog).append(eq("RENTAL_STARTED"), contains("vehicleId=m2"));
    }

    @Test
    void handleTelemetrySetsTelemetryOnVehicle() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("tele1", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.AVAILABLE);

        TelemetryData t = new TelemetryData("tele1", 12.3, 45.6, 80, 21.0);

        when(vehicleStorage.findById("tele1")).thenReturn(Optional.of(v));

        controller.handleTelemetry(t);

        assertEquals(t, v.getTelemetry());
        verify(vehicleStorage).save(v);
    }

    @Test
    void handleTelemetryFaultDoesNotOverrideEmergencyLock() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("tele2", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.EMERGENCY_LOCK);
        v.setRentalActive(false);

        TelemetryData t = new TelemetryData("tele2", 0.0, 0.0, 50, 20.0);
        t.setFault(true);

        when(vehicleStorage.findById("tele2")).thenReturn(Optional.of(v));

        controller.handleTelemetry(t);

        assertEquals(VehicleState.EMERGENCY_LOCK, v.getState());
        verify(auditLog, never()).append(eq("FAULT_DETECTED"), anyString());
    }

    @Test
    void reserveVehicleFromRelocatingThrows() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("r2", VehicleType.E_SCOOTER, City.LONDON);
        v.setState(VehicleState.RELOCATING);

        when(vehicleStorage.findById("r2")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> controller.reserveVehicle("r2", City.LONDON));
    }
}
