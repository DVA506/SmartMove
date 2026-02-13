package com.smartmove.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public class AuditLogService {

    private final Path logFile;
    private final ObjectMapper mapper = new ObjectMapper();

    private long nextId = 1;
    private String lastChecksum = "GENESIS";

    public AuditLogService(Path logFile) {
        this.logFile = logFile;
        initialize();
    }

    private void initialize() {
        try {
            if (!Files.exists(logFile)) {
                Files.createDirectories(logFile.getParent());
                Files.writeString(logFile, "");
                return;
            }

            List<String> lines = Files.readAllLines(logFile);

            for (String line : lines) {
                if (line.isBlank()) continue;

                AuditLogEntry entry =
                        mapper.readValue(line, AuditLogEntry.class);

                nextId = Math.max(nextId, entry.id + 1);
                lastChecksum = entry.checksum;
            }

            // Optional: verify integrity at startup
            verifyIntegrity();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize audit log", e);
        }
    }

    public synchronized void append(String event, String details) {
        try {
            long id = nextId++;
            long timestamp = System.currentTimeMillis();

            String checksum = sha256(
                    id + "|" + timestamp + "|" + event + "|" + details + "|" + lastChecksum
            );

            AuditLogEntry entry = new AuditLogEntry(
                    id,
                    timestamp,
                    event,
                    details,
                    lastChecksum,
                    checksum
            );

            String jsonLine = mapper.writeValueAsString(entry) + "\n";

            Files.writeString(
                    logFile,
                    jsonLine,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            lastChecksum = checksum;

        } catch (IOException e) {
            throw new RuntimeException("Audit log write failed", e);
        }
    }

    private void verifyIntegrity() {
        try {
            String previous = "GENESIS";
            long expectedId = 1;

            List<String> lines = Files.readAllLines(logFile);

            for (String line : lines) {
                if (line.isBlank()) continue;

                AuditLogEntry entry =
                        mapper.readValue(line, AuditLogEntry.class);

                if (entry.id != expectedId) {
                    throw new IllegalStateException("Audit ID sequence broken");
                }

                if (!previous.equals(entry.previousChecksum)) {
                    throw new IllegalStateException("Audit chain broken");
                }

                String recalculated = sha256(
                        entry.id + "|" +
                        entry.timestamp + "|" +
                        entry.event + "|" +
                        entry.details + "|" +
                        entry.previousChecksum
                );

                if (!recalculated.equals(entry.checksum)) {
                    throw new IllegalStateException("Audit checksum invalid");
                }

                previous = entry.checksum;
                expectedId++;
            }

        } catch (Exception e) {
            throw new RuntimeException("Audit log integrity verification failed", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
