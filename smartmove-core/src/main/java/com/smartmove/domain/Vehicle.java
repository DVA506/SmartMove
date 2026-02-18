package com.smartmove.domain;

import java.util.Objects;
import java.util.UUID;
import com.smartmove.telemetry.TelemetryData;

public class Vehicle {

    private String id;
    private VehicleType type;
    private VehicleState state;
    private City city;

    private TelemetryData telemetry;

    // Used for theft detection (no active rental but moving)
    private boolean rentalActive;

    // Required for JSON deserialization
    public Vehicle() {}

    public Vehicle(VehicleType type, City city) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.city = city;
        this.state = VehicleState.AVAILABLE;
        this.rentalActive = false;
    }

    public Vehicle(String id, VehicleType type, City city) {
        this.id = id;
        this.type = type;
        this.city = city;
        this.state = VehicleState.AVAILABLE;
        this.rentalActive = false;
    }

    // -------------------------
    // Getters
    // -------------------------

    public String getId() {
        return id;
    }

    public VehicleType getType() {
        return type;
    }

    public VehicleState getState() {
        return state;
    }

    public City getCity() {
        return city;
    }

    public TelemetryData getTelemetry() {
        return telemetry;
    }

    public boolean isRentalActive() {
        return rentalActive;
    }

    // -------------------------
    // Setters
    // -------------------------

    public void setId(String id) {
        this.id = id;
    }

    public void setType(VehicleType type) {
        this.type = type;
    }

    public void setState(VehicleState state) {
        this.state = state;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public void setTelemetry(TelemetryData telemetry) {
        this.telemetry = telemetry;
    }

    public void setRentalActive(boolean rentalActive) {
        this.rentalActive = rentalActive;
    }

    // -------------------------
    // Utility Methods
    // -------------------------

    /**
     * Used for rollback safety in controller.
     * Creates a deep copy of this vehicle.
     */
    public Vehicle copy() {
        Vehicle copy = new Vehicle();
        copy.id = this.id;
        copy.type = this.type;
        copy.state = this.state;
        copy.city = this.city;
        copy.rentalActive = this.rentalActive;

        if (this.telemetry != null) {
            copy.telemetry = this.telemetry.copy();
        }

        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle vehicle)) return false;
        return Objects.equals(id, vehicle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", state=" + state +
                ", city=" + city +
                '}';
    }
}
