package com.smartmove.zones;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmove.domain.City;

import java.nio.file.*;
import java.util.*;

public class JsonZoneRepository implements ZoneRepository {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, List<RestrictedZone>> cache = new HashMap<>();

    public JsonZoneRepository(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "{}");
            }
            String json = Files.readString(file);
            cache = mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to load zones from " + file, e);
        }
    }

    @Override
    public List<RestrictedZone> getZonesForCity(City city) {
        return cache.getOrDefault(city.name(), List.of());
    }
}
