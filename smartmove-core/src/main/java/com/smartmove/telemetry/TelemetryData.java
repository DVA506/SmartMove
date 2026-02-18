package com.smartmove.telemetry;

public class TelemetryData {

    private String vehicleId;

    // -------------------------
    // GPS
    // -------------------------
    private double latitude;
    private double longitude;

    // -------------------------
    // Core Telemetry
    // -------------------------
    private int batteryPercent;     // 0 - 100
    private double temperatureC;    // internal component temperature

    // -------------------------
    // Hardware / Safety Sensors
    // -------------------------

    // Milan moped rule
    private boolean helmetPresent;

    // Theft detection
    private boolean movementDetected;

    // NEW: Generic hardware fault flag
    // Used to trigger Maintenance state
    private boolean fault;

    // Required for JSON deserialization
    public TelemetryData() {}

    public TelemetryData(String vehicleId,
                         double latitude,
                         double longitude,
                         int batteryPercent,
                         double temperatureC) {
        this.vehicleId = vehicleId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.batteryPercent = batteryPercent;
        this.temperatureC = temperatureC;
    }

    // -------------------------
    // Getters
    // -------------------------

    public String getVehicleId() {
        return vehicleId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getBatteryPercent() {
        return batteryPercent;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public boolean isHelmetPresent() {
        return helmetPresent;
    }

    public boolean isMovementDetected() {
        return movementDetected;
    }

    public boolean isFault() {
        return fault;
    }

    // -------------------------
    // Setters
    // -------------------------

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setBatteryPercent(int batteryPercent) {
        this.batteryPercent = batteryPercent;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public void setHelmetPresent(boolean helmetPresent) {
        this.helmetPresent = helmetPresent;
    }

    public void setMovementDetected(boolean movementDetected) {
        this.movementDetected = movementDetected;
    }

    public void setFault(boolean fault) {
        this.fault = fault;
    }

    // -------------------------
    // Utility
    // -------------------------

    /**
     * Deep copy for rollback safety (used by Vehicle.copy()).
     */
    public TelemetryData copy() {
        TelemetryData t = new TelemetryData();
        t.vehicleId = this.vehicleId;
        t.latitude = this.latitude;
        t.longitude = this.longitude;
        t.batteryPercent = this.batteryPercent;
        t.temperatureC = this.temperatureC;
        t.helmetPresent = this.helmetPresent;
        t.movementDetected = this.movementDetected;
        t.fault = this.fault;   // IMPORTANT: include new field
        return t;
    }

    @Override
    public String toString() {
        return "TelemetryData{" +
                "vehicleId='" + vehicleId + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", batteryPercent=" + batteryPercent +
                ", temperatureC=" + temperatureC +
                ", helmetPresent=" + helmetPresent +
                ", movementDetected=" + movementDetected +
                ", fault=" + fault +
                '}';
    }
}
