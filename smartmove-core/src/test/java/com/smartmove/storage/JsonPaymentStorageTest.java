package com.smartmove.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.smartmove.domain.City;
import com.smartmove.domain.Payment;

class JsonPaymentStorageTest {

    private Path tempFile() throws Exception {
        return Files.createTempFile("payments", ".json");
    }

    @Test
    void createsFileIfMissingAndStartsEmpty() throws Exception {
        Path file = tempFile();
        Files.deleteIfExists(file);

        JsonPaymentStorage storage = new JsonPaymentStorage(file);

        assertTrue(Files.exists(file));
        assertTrue(storage.findAll().isEmpty());
    }

    @Test
    void saveAddsPaymentAndCanBeReadBack() throws Exception {
        Path file = tempFile();
        JsonPaymentStorage storage = new JsonPaymentStorage(file);

        Payment p = new Payment("v1", City.LONDON, 10.0, 5.0);

        storage.save(p);

        List<Payment> all = storage.findAll();

        assertEquals(1, all.size());
        assertEquals(p.getId(), all.get(0).getId());
    }

    @Test
    void saveMultiplePaymentsPersistCorrectly() throws Exception {
        Path file = tempFile();
        JsonPaymentStorage storage = new JsonPaymentStorage(file);

        Payment p1 = new Payment("v1", City.LONDON, 10.0, 5.0);
        Payment p2 = new Payment("v2", City.ROME, 8.0, 0.0);

        storage.save(p1);
        storage.save(p2);

        List<Payment> all = storage.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void findAllReturnsCopyNotInternalList() throws Exception {
        Path file = tempFile();
        JsonPaymentStorage storage = new JsonPaymentStorage(file);

        Payment p = new Payment("v1", City.LONDON, 10.0, 5.0);
        storage.save(p);

        List<Payment> list = storage.findAll();
        list.clear();

        assertEquals(1, storage.findAll().size());
    }

    @Test
    void handlesBlankFileGracefully() throws Exception {
        Path file = tempFile();
        Files.writeString(file, "");

        JsonPaymentStorage storage = new JsonPaymentStorage(file);

        assertTrue(storage.findAll().isEmpty());
    }

    @Test
    void classCanBeInstantiated() {
        UserStorage storage = new UserStorage();
        assertNotNull(storage);
    }
}