package com.smartmove.audit;

public class AuditLogEntry {

    public long id;
    public long timestamp;
    public String event;
    public String details;
    public String previousChecksum;
    public String checksum;

    public AuditLogEntry() {}

    public AuditLogEntry(long id,
                         long timestamp,
                         String event,
                         String details,
                         String previousChecksum,
                         String checksum) {

        this.id = id;
        this.timestamp = timestamp;
        this.event = event;
        this.details = details;
        this.previousChecksum = previousChecksum;
        this.checksum = checksum;
    }
}
