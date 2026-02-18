package com.smartmove.zones;

import com.smartmove.domain.City;
import com.smartmove.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneServiceTest {

    @Test
    void returnsFalseWhenNoZonesForCity() {
        ZoneRepository repo = mock(ZoneRepository.class);
        when(repo.getZonesForCity(City.LONDON)).thenReturn(List.of());

        ZoneService service = new ZoneService(repo);

        assertFalse(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 51.5, -0.12));
        verify(repo).getZonesForCity(City.LONDON);
    }

    @Test
    void returnsTrueWhenPointInsideRectangleAndVehicleTypesNull() {
        ZoneRepository repo = mock(ZoneRepository.class);

        RestrictedZone z = new RestrictedZone();
        z.minLat = 10; z.maxLat = 20;
        z.minLon = 30; z.maxLon = 40;
        z.vehicleTypes = null;

        when(repo.getZonesForCity(City.LONDON)).thenReturn(List.of(z));

        ZoneService service = new ZoneService(repo);

        assertTrue(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 15, 35));
    }

    @Test
    void returnsFalseWhenVehicleTypeNotAllowedEvenIfInsideRectangle() {
        ZoneRepository repo = mock(ZoneRepository.class);

        RestrictedZone z = new RestrictedZone();
        z.minLat = 10; z.maxLat = 20;
        z.minLon = 30; z.maxLon = 40;
        z.vehicleTypes = Set.of("CAR");


        when(repo.getZonesForCity(City.LONDON)).thenReturn(List.of(z));

        ZoneService service = new ZoneService(repo);

        assertFalse(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 15, 35));
    }

    @Test
    void returnsFalseWhenOutsideRectangle() {
        ZoneRepository repo = mock(ZoneRepository.class);

        RestrictedZone z = new RestrictedZone();
        z.minLat = 10; z.maxLat = 20;
        z.minLon = 30; z.maxLon = 40;
        z.vehicleTypes = null;

        when(repo.getZonesForCity(City.LONDON)).thenReturn(List.of(z));

        ZoneService service = new ZoneService(repo);

        assertFalse(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 25, 35));
        assertFalse(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 15, 50));
    }

    @Test
    void returnsTrueOnBoundaryValues() {
        ZoneRepository repo = mock(ZoneRepository.class);

        RestrictedZone z = new RestrictedZone();
        z.minLat = 10; z.maxLat = 20;
        z.minLon = 30; z.maxLon = 40;
        z.vehicleTypes = Set.of(VehicleType.E_SCOOTER.name());

        when(repo.getZonesForCity(City.LONDON)).thenReturn(List.of(z));

        ZoneService service = new ZoneService(repo);

        assertTrue(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 10, 30));
        assertTrue(service.isRestricted(City.LONDON, VehicleType.E_SCOOTER, 20, 40));
    }
}
