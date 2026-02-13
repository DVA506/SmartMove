package com.smartmove.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmove.domain.Payment;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JsonPaymentStorage implements PaymentStorage {

    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

    public JsonPaymentStorage(Path filePath) {
        this.filePath = filePath;
        ensureFileExists();
    }

    private void ensureFileExists() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "[]", StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare payment storage file: " + filePath, e);
        }
    }

    @Override
    public void save(Payment payment) {
        rw.writeLock().lock();
        try {
            List<Payment> all = findAllInternal();
            all.add(payment);

            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(all),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save payment", e);
        } finally {
            rw.writeLock().unlock();
        }
    }

    @Override
    public List<Payment> findAll() {
        rw.readLock().lock();
        try {
            return new ArrayList<>(findAllInternal());
        } finally {
            rw.readLock().unlock();
        }
    }

    private List<Payment> findAllInternal() {
        try {
            String json = Files.readString(filePath);
            if (json == null || json.isBlank()) json = "[]";
            return mapper.readValue(json, new TypeReference<List<Payment>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to read payments", e);
        }
    }
}
