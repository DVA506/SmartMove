package com.smartmove.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TelemetryDataTest {

    @Test
    void constructorSetsAllFields() {
        TelemetryData t = new TelemetryData("v1", 10.0, 20.0, 75, 35.5);

        assertEquals("v1", t.getVehicleId());
        assertEquals(10.0, t.getLatitude());
        assertEquals(20.0, t.getLongitude());
        assertEquals(75, t.getBatteryPercent());
        assertEquals(35.5, t.getTemperatureC());
    }

    @Test
    void settersAndGettersWorkCorrectly() {
        TelemetryData t = new TelemetryData();

        t.setVehicleId("v2");
        t.setLatitude(1.1);
        t.setLongitude(2.2);
        t.setBatteryPercent(90);
        t.setTemperatureC(22.0);
        t.setHelmetPresent(true);
        t.setMovementDetected(true);
        t.setFault(true);

        assertEquals("v2", t.getVehicleId());
        assertEquals(1.1, t.getLatitude());
        assertEquals(2.2, t.getLongitude());
        assertEquals(90, t.getBatteryPercent());
        assertEquals(22.0, t.getTemperatureC());
        assertTrue(t.isHelmetPresent());
        assertTrue(t.isMovementDetected());
        assertTrue(t.isFault());
    }

    @Test
    void copyCreatesDeepCopy() {
        TelemetryData original = new TelemetryData("v3", 5.0, 6.0, 40, 18.0);
        original.setHelmetPresent(true);
        original.setMovementDetected(true);
        original.setFault(true);

        TelemetryData copy = original.copy();

        assertNotSame(original, copy);

        assertEquals(original.getVehicleId(), copy.getVehicleId());
        assertEquals(original.getLatitude(), copy.getLatitude());
        assertEquals(original.getLongitude(), copy.getLongitude());
        assertEquals(original.getBatteryPercent(), copy.getBatteryPercent());
        assertEquals(original.getTemperatureC(), copy.getTemperatureC());
        assertEquals(original.isHelmetPresent(), copy.isHelmetPresent());
        assertEquals(original.isMovementDetected(), copy.isMovementDetected());
        assertEquals(original.isFault(), copy.isFault());
    }

    @Test
    void toStringContainsKeyFields() {
        TelemetryData t = new TelemetryData("v4", 1.0, 2.0, 50, 20.0);
        t.setFault(true);

        String result = t.toString();

        assertTrue(result.contains("v4"));
        assertTrue(result.contains("batteryPercent=50"));
        assertTrue(result.contains("fault=true"));
    }
}