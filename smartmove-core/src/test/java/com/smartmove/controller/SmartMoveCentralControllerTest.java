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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

        // Correct constructor with all parameters
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

    // --- New test for reliability tactics on sendTelemetry ---
    @Test
    void sendTelemetry_queueFull_retriesAndLogs() throws Exception { // throws Exception covers reflection exceptions
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        Vehicle v = new Vehicle("v7", VehicleType.E_SCOOTER, City.LONDON);

        // Mock the queue to trigger retry
        BlockingQueue<TelemetryData> queue = mock(BlockingQueue.class);
        when(queue.offer(any(TelemetryData.class), anyLong(), any()))
                .thenReturn(false)  // first attempt fails
                .thenReturn(true);  // second attempt succeeds

        // Replace the private telemetryQueue using reflection
        java.lang.reflect.Field field = SmartMoveCentralController.class.getDeclaredField("telemetryQueue");
        field.setAccessible(true);
        field.set(controller, queue);

        TelemetryData t = new TelemetryData("v7", 0.0, 0.0, 50, 20.0);

        controller.sendTelemetry(t);

        verify(queue, atLeast(2)).offer(eq(t), anyLong(), any());
        verify(auditLog).append(eq("TELEMETRY_RETRY"), contains("vehicleId=v7"));
    }
    @Test
    void sendTelemetry_queueInterrupted_logsAndStops() throws Exception {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        BlockingQueue<TelemetryData> queue = mock(BlockingQueue.class);
        when(queue.offer(any(TelemetryData.class), anyLong(), any()))
                .thenThrow(new InterruptedException());

        java.lang.reflect.Field field = SmartMoveCentralController.class.getDeclaredField("telemetryQueue");
        field.setAccessible(true);
        field.set(controller, queue);

        TelemetryData t = new TelemetryData("v8", 0.0, 0.0, 50, 20.0);

        controller.sendTelemetry(t);

        verify(auditLog).append(eq("TELEMETRY_FAILED"), contains("vehicleId=v8"));
    }
    @Test
    void setTelemetryQueue_replacesQueueContents() {
        controller = new SmartMoveCentralController(vehicleStorage, auditLog, zoneService, paymentStorage);

        // Create a test queue with some telemetry
        BlockingQueue<TelemetryData> testQueue = new LinkedBlockingQueue<>();
        TelemetryData t1 = new TelemetryData("test1", 0.0, 0.0, 50, 20.0);
        TelemetryData t2 = new TelemetryData("test2", 0.0, 0.0, 50, 20.0);
        testQueue.offer(t1);
        testQueue.offer(t2);

        // Call the helper method
        controller.setTelemetryQueue(testQueue);

        // Assert the telemetryQueue has the test items
        assertEquals(2, controller.getTelemetryQueueSize());
    }
}