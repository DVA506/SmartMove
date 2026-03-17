package com.smartmove.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.smartmove.telemetry.TelemetryData;

class VehicleTest {

    @Test
    void constructorWithTypeAndCity_setsDefaults() {
        Vehicle vehicle = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);

        assertNotNull(vehicle.getId());
        assertEquals(VehicleType.E_SCOOTER, vehicle.getType());
        assertEquals(City.LONDON, vehicle.getCity());
        assertEquals(VehicleState.AVAILABLE, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
    }

    @Test
    void constructorWithId_setsDefaults() {
        Vehicle vehicle = new Vehicle("v1", VehicleType.MOPED, City.ROME);

        assertEquals("v1", vehicle.getId());
        assertEquals(VehicleType.MOPED, vehicle.getType());
        assertEquals(City.ROME, vehicle.getCity());
        assertEquals(VehicleState.AVAILABLE, vehicle.getState());
        assertFalse(vehicle.isRentalActive());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        Vehicle vehicle = new Vehicle();

        TelemetryData telemetry = new TelemetryData("v2", 10.0, 20.0, 50, 25.0);

        vehicle.setId("v2");
        vehicle.setType(VehicleType.E_SCOOTER);
        vehicle.setState(VehicleState.IN_USE);
        vehicle.setCity(City.MILAN);
        vehicle.setTelemetry(telemetry);
        vehicle.setRentalActive(true);

        assertEquals("v2", vehicle.getId());
        assertEquals(VehicleType.E_SCOOTER, vehicle.getType());
        assertEquals(VehicleState.IN_USE, vehicle.getState());
        assertEquals(City.MILAN, vehicle.getCity());
        assertEquals(telemetry, vehicle.getTelemetry());
        assertTrue(vehicle.isRentalActive());
    }

    @Test
    void copy_createsDeepCopy() {
        Vehicle original = new Vehicle("v3", VehicleType.E_SCOOTER, City.LONDON);
        original.setState(VehicleState.RESERVED);
        original.setRentalActive(true);

        TelemetryData telemetry = new TelemetryData("v3", 1.0, 2.0, 80, 30.0);
        telemetry.setFault(true);
        original.setTelemetry(telemetry);

        Vehicle copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.getState(), copy.getState());
        assertEquals(original.getCity(), copy.getCity());
        assertEquals(original.isRentalActive(), copy.isRentalActive());

        assertNotSame(original.getTelemetry(), copy.getTelemetry());
        assertEquals(original.getTelemetry().getVehicleId(), copy.getTelemetry().getVehicleId());
        assertEquals(original.getTelemetry().getLatitude(), copy.getTelemetry().getLatitude());
        assertEquals(original.getTelemetry().getLongitude(), copy.getTelemetry().getLongitude());
        assertEquals(original.getTelemetry().getBatteryPercent(), copy.getTelemetry().getBatteryPercent());
        assertEquals(original.getTelemetry().getTemperatureC(), copy.getTelemetry().getTemperatureC());
        assertEquals(original.getTelemetry().isFault(), copy.getTelemetry().isFault());
    }

    @Test
    void copy_withNullTelemetry_keepsTelemetryNull() {
        Vehicle original = new Vehicle("v4", VehicleType.MOPED, City.ROME);
        original.setTelemetry(null);

        Vehicle copy = original.copy();

        assertNotSame(original, copy);
        assertNull(copy.getTelemetry());
    }

    @Test
    void equalsAndHashCode_sameId_areEqual() {
        Vehicle v1 = new Vehicle();
        v1.setId("same-id");

        Vehicle v2 = new Vehicle();
        v2.setId("same-id");

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void equals_differentId_notEqual() {
        Vehicle v1 = new Vehicle();
        v1.setId("id-1");

        Vehicle v2 = new Vehicle();
        v2.setId("id-2");

        assertNotEquals(v1, v2);
    }

    @Test
    void equals_nullAndDifferentType_returnFalse() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId("v5");

        assertNotEquals(vehicle, null);
        assertNotEquals(vehicle, "not-a-vehicle");
    }

    @Test
    void toString_containsKeyFields() {
        Vehicle vehicle = new Vehicle("v6", VehicleType.E_SCOOTER, City.LONDON);
        vehicle.setState(VehicleState.MAINTENANCE);

        String result = vehicle.toString();

        assertTrue(result.contains("v6"));
        assertTrue(result.contains("E_SCOOTER"));
        assertTrue(result.contains("MAINTENANCE"));
        assertTrue(result.contains("LONDON"));
    }
}