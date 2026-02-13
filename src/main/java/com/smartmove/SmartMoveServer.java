package com.smartmove;

import com.smartmove.audit.AuditLogService;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.storage.JsonVehicleStorage;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.zones.JsonZoneRepository;
import com.smartmove.zones.ZoneRepository;
import com.smartmove.zones.ZoneService;
import com.smartmove.storage.JsonPaymentStorage;
import com.smartmove.storage.PaymentStorage;


import java.nio.file.Paths;

public class SmartMoveServer {

    public static void main(String[] args) {
        // Storage (JSON files)
        VehicleStorage vehicleStorage = new JsonVehicleStorage(Paths.get("data/vehicles.json"));

        // Audit log (file append) - make sure your AuditLogService supports this path constructor
        AuditLogService auditLogService = new AuditLogService(Paths.get("data/audit-log.jsonl"));

        // Restricted zones (JSON config)
        ZoneRepository zoneRepo = new JsonZoneRepository(Paths.get("data/restricted-zones.json"));
        ZoneService zoneService = new ZoneService(zoneRepo);

        // Payment zones (JSON files)
        PaymentStorage payments = new JsonPaymentStorage(Paths.get("data/payments.json"));

        // Controller
        SmartMoveCentralController controller =
                new SmartMoveCentralController(vehicleStorage, auditLogService, zoneService, payments);

        // Keep app alive (optional)
        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown));

        System.out.println("SmartMove core engine started.");
        System.out.println("Vehicles persisted at: data/vehicles.json");
        System.out.println("Zones loaded from: data/restricted-zones.json");
        System.out.println("Audit log at: data/audit-log.jsonl");

        // If you have no HTTP server, you can run a simple simulation here.
    }
}
