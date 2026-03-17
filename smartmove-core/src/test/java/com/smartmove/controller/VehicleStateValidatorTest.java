package com.smartmove.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.smartmove.domain.VehicleState;

class VehicleStateValidatorTest {

    private final VehicleStateValidator validator = new VehicleStateValidator();

    @Test
    void availableAllowsReserved() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.AVAILABLE, VehicleState.RESERVED));
    }

    @Test
    void reservedAllowsInUse() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.RESERVED, VehicleState.IN_USE));
    }

    @Test
    void inUseAllowsMaintenance() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.IN_USE, VehicleState.MAINTENANCE));
    }

    @Test
    void maintenanceAllowsAvailable() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.MAINTENANCE, VehicleState.AVAILABLE));
    }

    @Test
    void relocatingAllowsEmergencyLock() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.RELOCATING, VehicleState.EMERGENCY_LOCK));
    }

    @Test
    void emergencyLockAllowsMaintenance() {
        assertDoesNotThrow(() -> validator.validateTransition(VehicleState.EMERGENCY_LOCK, VehicleState.MAINTENANCE));
    }

    @Test
    void availableToInUseThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.validateTransition(VehicleState.AVAILABLE, VehicleState.IN_USE));

        assertTrue(ex.getMessage().contains("AVAILABLE -> IN_USE"));
    }

    @Test
    void reservedToRelocatingThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.validateTransition(VehicleState.RESERVED, VehicleState.RELOCATING));

        assertTrue(ex.getMessage().contains("RESERVED -> RELOCATING"));
    }

    @Test
    void emergencyLockToAvailableThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.validateTransition(VehicleState.EMERGENCY_LOCK, VehicleState.AVAILABLE));

        assertTrue(ex.getMessage().contains("EMERGENCY_LOCK -> AVAILABLE"));
    }
}