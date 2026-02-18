package com.smartmove.zones;

import java.util.Set;

public class RestrictedZone {
    public String id;
    public String type; // "RECTANGLE"
    public double minLat;
    public double maxLat;
    public double minLon;
    public double maxLon;
    public Set<String> vehicleTypes;
}
