package com.smartmove.zones;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.smartmove.domain.City;

class JsonZoneRepositoryTest {

    @Test
    void createsFileIfMissingAndReturnsEmpty() throws Exception {
        Path file = Files.createTempFile("zones", ".json");
        Files.deleteIfExists(file);

        JsonZoneRepository repo = new JsonZoneRepository(file);

        List<RestrictedZone> zones = repo.getZonesForCity(City.ROME);

        assertTrue(Files.exists(file));
        assertTrue(zones.isEmpty());
    }

    @Test
    void loadsZonesFromJsonCorrectly() throws Exception {
        Path file = Files.createTempFile("zones", ".json");

        String json = """
                {
                  "ROME": [
                    {
                      "minLat": 1.0,
                      "maxLat": 2.0,
                      "minLon": 3.0,
                      "maxLon": 4.0,
                      "vehicleTypes": ["E_SCOOTER"]
                    }
                  ]
                }
                """;

        Files.writeString(file, json);

        JsonZoneRepository repo = new JsonZoneRepository(file);

        List<RestrictedZone> zones = repo.getZonesForCity(City.ROME);

        assertEquals(1, zones.size());
        assertEquals(1.0, zones.get(0).minLat);
        assertEquals(2.0, zones.get(0).maxLat);
    }

    @Test
    void unknownCityReturnsEmptyList() throws Exception {
        Path file = Files.createTempFile("zones", ".json");

        Files.writeString(file, "{}");

        JsonZoneRepository repo = new JsonZoneRepository(file);

        List<RestrictedZone> zones = repo.getZonesForCity(City.LONDON);

        assertNotNull(zones);
        assertTrue(zones.isEmpty());
    }
}