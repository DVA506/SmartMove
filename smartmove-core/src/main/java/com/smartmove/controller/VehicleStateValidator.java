package com.smartmove.controller;

import com.smartmove.domain.VehicleState;

import java.util.EnumSet;
import java.util.Set;

public class VehicleStateValidator {

    public void validateTransition(VehicleState from, VehicleState to) {
        if (!allowedTargets(from).contains(to)) {
            throw new IllegalStateException("Invalid transition " + from + " -> " + to);
        }
    }

    private Set<VehicleState> allowedTargets(VehicleState from) {
        return switch (from) {
            case AVAILABLE -> EnumSet.of(
                    VehicleState.RESERVED,
                    VehicleState.RELOCATING,
                    VehicleState.EMERGENCY_LOCK
            );
            case RESERVED -> EnumSet.of(
                    VehicleState.IN_USE,
                    VehicleState.AVAILABLE,
                    VehicleState.EMERGENCY_LOCK
            );
            case IN_USE -> EnumSet.of(
                    VehicleState.AVAILABLE,
                    VehicleState.MAINTENANCE,
                    VehicleState.EMERGENCY_LOCK
            );
            case MAINTENANCE -> EnumSet.of(
                    VehicleState.AVAILABLE,
                    VehicleState.EMERGENCY_LOCK
            );
            case RELOCATING -> EnumSet.of(
                    VehicleState.AVAILABLE,
                    VehicleState.EMERGENCY_LOCK
            );
            case EMERGENCY_LOCK -> EnumSet.of(VehicleState.MAINTENANCE);
        };
    }
}