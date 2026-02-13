package com.smartmove.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmove.domain.Vehicle;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-backed VehicleStorage using a single JSON file.
 * - Loads all vehicles on startup into an in-memory cache
 * - Persists changes back to disk using atomic temp-file replacement
 *
 * Meets lab requirement: local JSON persistence (no DB).
 */
public class JsonVehicleStorage implements VehicleStorage {

    private final Path filePath;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    // In-memory cache for performance
    private final Map<String, Vehicle> cache = new HashMap<>();

    public JsonVehicleStorage(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        ensureFileExists();
        loadFromDisk();
    }

    private void ensureFileExists() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);

            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "[]", StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare vehicle storage file: " + filePath, e);
        }
    }

    private void loadFromDisk() {
        rwLock.writeLock().lock();
        try {
            String json = Files.readString(filePath);
            if (json == null || json.isBlank()) json = "[]";

            List<Vehicle> vehicles = mapper.readValue(json, new TypeReference<List<Vehicle>>() {});
            cache.clear();

            for (Vehicle v : vehicles) {
                if (v == null) continue;
                String id = v.getId(); // IMPORTANT: your Vehicle must have getId()
                cache.put(id, v);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load vehicles from JSON: " + filePath, e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void flushToDiskAtomic() throws IOException {
        // Write to temp file first, then atomically replace main file
        Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");

        List<Vehicle> vehicles = new ArrayList<>(cache.values());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vehicles);

        Files.writeString(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // ATOMIC_MOVE is best-effort; if filesystem doesn't support it, it may throw
        try {
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Optional<Vehicle> findById(String id) {
        rwLock.readLock().lock();
        try {
            return Optional.ofNullable(cache.get(id));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public List<Vehicle> findAll() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(cache.values());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void save(Vehicle vehicle) {
        if (vehicle == null) throw new IllegalArgumentException("vehicle cannot be null");
        if (vehicle.getId() == null || vehicle.getId().isBlank())
            throw new IllegalArgumentException("vehicle.id cannot be null/blank");

        rwLock.writeLock().lock();
        try {
            cache.put(vehicle.getId(), vehicle);
            flushToDiskAtomic();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save vehicle to JSON file: " + filePath, e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteById(String id) {
        rwLock.writeLock().lock();
        try {
            cache.remove(id);
            flushToDiskAtomic();
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete vehicle from JSON file: " + filePath, e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
