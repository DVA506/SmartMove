package com.smartmove.storage;

import com.smartmove.domain.City;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonVehicleStorageTest {

    @TempDir
    Path tempDir;

    private Path storageFile() {
        return tempDir.resolve("vehicles.json");
    }

    @Test
    void createsFileIfMissing_andLoadsEmptyList() {
        Path file = storageFile();
        assertFalse(Files.exists(file));

        JsonVehicleStorage storage = new JsonVehicleStorage(file);

        assertTrue(Files.exists(file));
        assertTrue(storage.findAll().isEmpty());
    }

    @Test
    void loadsVehiclesFromDiskOnStartup() throws IOException {
        Path file = storageFile();

        Vehicle v = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);
        String json = "[ " + toJsonVehicle(v) + " ]";
        Files.writeString(file, json);

        JsonVehicleStorage storage = new JsonVehicleStorage(file);

        Optional<Vehicle> loaded = storage.findById(v.getId());
        assertTrue(loaded.isPresent());
        assertEquals(v.getId(), loaded.get().getId());
    }

    @Test
    void savePersistsAndCanBeReadBackByNewInstance() {
        Path file = storageFile();
        JsonVehicleStorage storage = new JsonVehicleStorage(file);

        Vehicle v = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);
        storage.save(v);

        JsonVehicleStorage storage2 = new JsonVehicleStorage(file);
        assertTrue(storage2.findById(v.getId()).isPresent());
    }

    @Test
    void deleteRemovesFromCacheAndDisk() {
        Path file = storageFile();
        JsonVehicleStorage storage = new JsonVehicleStorage(file);

        Vehicle v = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);
        storage.save(v);
        assertTrue(storage.findById(v.getId()).isPresent());

        storage.deleteById(v.getId());
        assertFalse(storage.findById(v.getId()).isPresent());

        JsonVehicleStorage storage2 = new JsonVehicleStorage(file);
        assertFalse(storage2.findById(v.getId()).isPresent());
    }

    @Test
    void saveRejectsNullVehicle() {
        JsonVehicleStorage storage = new JsonVehicleStorage(storageFile());
        assertThrows(IllegalArgumentException.class, () -> storage.save(null));
    }

    @Test
    void saveRejectsBlankId() {
        JsonVehicleStorage storage = new JsonVehicleStorage(storageFile());

        Vehicle v = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);

        Vehicle bad = new Vehicle(v.getType(), v.getCity()) {
            @Override public String getId() { return " "; }
        };

        assertThrows(IllegalArgumentException.class, () -> storage.save(bad));
    }

    @Test
    void throwsRuntimeExceptionOnInvalidJson() throws IOException {
        Path file = storageFile();
        Files.writeString(file, "{not valid json");

        assertThrows(RuntimeException.class, () -> new JsonVehicleStorage(file));
    }

    private String toJsonVehicle(Vehicle v) {
    return "{"
            + "\"id\":\"" + v.getId() + "\","
            + "\"type\":\"" + v.getType().name() + "\","
            + "\"state\":\"" + v.getState().name() + "\","
            + "\"city\":\"" + v.getCity().name() + "\","
            + "\"rentalActive\":false"
            + "}";
}

}
