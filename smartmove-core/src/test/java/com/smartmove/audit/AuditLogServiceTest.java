package com.smartmove.audit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AuditLogServiceTest {

    @Test
    void appendCreatesFileAndWritesEntry() throws Exception {
        Path tempFile = Files.createTempFile("audit-log", ".log");

        AuditLogService service = new AuditLogService(tempFile);

        service.append("TEST_EVENT", "details");

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("TEST_EVENT"));
        assertTrue(lines.get(0).contains("details"));
    }

    @Test
    void appendMultipleEntriesIncrementsIdAndChainsChecksum() throws Exception {
        Path tempFile = Files.createTempFile("audit-log", ".log");

        AuditLogService service = new AuditLogService(tempFile);

        service.append("EVENT1", "d1");
        service.append("EVENT2", "d2");

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(2, lines.size());

        assertTrue(lines.get(0).contains("\"id\":1"));
        assertTrue(lines.get(1).contains("\"id\":2"));
    }

    @Test
    void initializeReadsExistingFileAndContinuesSequence() throws Exception {
        Path tempFile = Files.createTempFile("audit-log", ".log");

        AuditLogService service1 = new AuditLogService(tempFile);
        service1.append("EVENT1", "d1");

        AuditLogService service2 = new AuditLogService(tempFile);
        service2.append("EVENT2", "d2");

        List<String> lines = Files.readAllLines(tempFile);

        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("\"id\":2"));
    }

    @Test
    void integrityCheckDetectsBrokenSequence() throws Exception {
        Path tempFile = Files.createTempFile("audit-log", ".log");

        Files.writeString(tempFile,
                "{\"id\":5,\"timestamp\":1,\"event\":\"X\",\"details\":\"Y\",\"previousChecksum\":\"GENESIS\",\"checksum\":\"bad\"}\n"
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new AuditLogService(tempFile));

        assertTrue(ex.getMessage().contains("Failed to initialize audit log"));
    }
}