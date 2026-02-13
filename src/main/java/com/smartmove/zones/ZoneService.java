package com.smartmove.zones;

import com.smartmove.domain.City;
import com.smartmove.domain.VehicleType;

public class ZoneService {
    private final ZoneRepository repo;

    public ZoneService(ZoneRepository repo) {
        this.repo = repo;
    }

    public boolean isRestricted(City city, VehicleType type, double lat, double lon) {
        for (RestrictedZone z : repo.getZonesForCity(city)) {
            if (z.vehicleTypes != null && !z.vehicleTypes.contains(type.name())) continue;

            // RECTANGLE check
            if (lat >= z.minLat && lat <= z.maxLat && lon >= z.minLon && lon <= z.maxLon) {
                return true;
            }
        }
        return false;
    }
}
