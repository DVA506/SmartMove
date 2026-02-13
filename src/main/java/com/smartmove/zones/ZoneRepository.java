package com.smartmove.zones;

import com.smartmove.domain.City;
import java.util.List;

public interface ZoneRepository {
    List<RestrictedZone> getZonesForCity(City city);
}
