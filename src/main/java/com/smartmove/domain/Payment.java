package com.smartmove.domain;

import java.time.Instant;
import java.util.UUID;

public class Payment {
    private String id;
    private String vehicleId;
    private City city;
    private double baseFare;
    private double congestionCharge;
    private double total;
    private String timestamp;

    public Payment() {}

    public Payment(String vehicleId, City city, double baseFare, double congestionCharge) {
        this.id = UUID.randomUUID().toString();
        this.vehicleId = vehicleId;
        this.city = city;
        this.baseFare = baseFare;
        this.congestionCharge = congestionCharge;
        this.total = baseFare + congestionCharge;
        this.timestamp = Instant.now().toString();
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public City getCity() { return city; }
    public double getBaseFare() { return baseFare; }
    public double getCongestionCharge() { return congestionCharge; }
    public double getTotal() { return total; }
    public String getTimestamp() { return timestamp; }
}
