package com.smartmove.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AuditLogEntryTest {

    @Test
    void constructorSetsAllFieldsCorrectly() {
        AuditLogEntry entry = new AuditLogEntry(
                1L,
                123456789L,
                "TEST_EVENT",
                "Provides details about the event",
                "prevHash",
                "hash"
        );

        assertEquals(1L, entry.id);
        assertEquals(123456789L, entry.timestamp);
        assertEquals("TEST_EVENT", entry.event);
        assertEquals("Provides details about the event", entry.details);
        assertEquals("prevHash", entry.previousChecksum);
        assertEquals("hash", entry.checksum);
    }

    @Test
    void defaultConstructorAllowsManualFieldSetting() {
        AuditLogEntry entry = new AuditLogEntry();

        entry.id = 2L;
        entry.timestamp = 999L;
        entry.event = "EVENT";
        entry.details = "Provides details about the event";
        entry.previousChecksum = "prev";
        entry.checksum = "curr";

        assertEquals(2L, entry.id);
        assertEquals(999L, entry.timestamp);
        assertEquals("EVENT", entry.event);
        assertEquals("Provides details about the event", entry.details);
        assertEquals("prev", entry.previousChecksum);
        assertEquals("curr", entry.checksum);
    }
}