package com.smartmove.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmove.audit.AuditLogService;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.*;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.storage.JsonVehicleStorage;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.zones.JsonZoneRepository;
import com.smartmove.zones.ZoneRepository;
import com.smartmove.zones.ZoneService;
import com.smartmove.storage.JsonPaymentStorage;
import com.smartmove.storage.PaymentStorage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;

public class SmartMoveApiServer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // Wire core engine
        VehicleStorage vehicleStorage = new JsonVehicleStorage(Paths.get("data/vehicles.json"));
        AuditLogService audit = new AuditLogService(Paths.get("data/audit-log.jsonl"));
        ZoneRepository zoneRepo = new JsonZoneRepository(Paths.get("data/restricted-zones.json"));
        PaymentStorage paymentStorage = new JsonPaymentStorage(Paths.get("data/payments.json"));

        ZoneService zones = new ZoneService(zoneRepo);


        SmartMoveCentralController controller = new SmartMoveCentralController(vehicleStorage, audit, zones, paymentStorage);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Register vehicle
        server.createContext("/vehicles", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use POST")); return; }

            VehicleCreateRequest req = readJson(ex, VehicleCreateRequest.class);
            Vehicle v = new Vehicle(req.type, req.city);
            controller.registerVehicle(v);
            json(ex, 200, Map.of("id", v.getId()));
        });

        // Get vehicle
        server.createContext("/vehicle", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"GET".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use GET")); return; }

            String query = ex.getRequestURI().getQuery(); // id=...
            String id = (query != null && query.startsWith("id=")) ? query.substring(3) : null;
            if (id == null || id.isBlank()) { json(ex, 400, Map.of("error","Missing id")); return; }

            var opt = controller.getVehicle(id);
            if (opt.isEmpty()) { json(ex, 404, Map.of("error","Not found")); return; }
            json(ex, 200, opt.get());
        });

        // Reserve
        server.createContext("/reserve", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use POST")); return; }

            ActionRequest req = readJson(ex, ActionRequest.class);
            controller.reserveVehicle(req.vehicleId, req.city);
            json(ex, 200, Map.of("ok", true));
        });

        // Start rental
        server.createContext("/start", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use POST")); return; }

            ActionRequest req = readJson(ex, ActionRequest.class);
            controller.startRental(req.vehicleId, req.city);
            json(ex, 200, Map.of("ok", true));
        });

        // End rental
        server.createContext("/end", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use POST")); return; }

            EndRequest req = readJson(ex, EndRequest.class);
            controller.endRental(req.vehicleId);
            json(ex, 200, Map.of("ok", true));
        });

        // Telemetry
        server.createContext("/telemetry", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, Map.of("error","Use POST")); return; }

            TelemetryRequest req = readJson(ex, TelemetryRequest.class);
            TelemetryData t = new TelemetryData(req.vehicleId, req.latitude, req.longitude, req.batteryPercent, req.temperatureC);
            t.setHelmetPresent(req.helmetPresent);
            t.setMovementDetected(req.movementDetected);
            t.setFault(req.fault);

            controller.sendTelemetry(t);
            json(ex, 200, Map.of("queued", true));
        });
        
        server.createContext("/health", ex -> {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
            json(ex, 200, Map.of("status", "ok"));
        });



        server.setExecutor(null);
        server.start();
        System.out.println("SmartMove API running on http://localhost:8080");
        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown));
    }

    // --- DTOs ---
    public static class VehicleCreateRequest { public VehicleType type; public City city; }
    public static class ActionRequest { public String vehicleId; public City city; }
    public static class EndRequest { public String vehicleId; }
    public static class TelemetryRequest {
        public String vehicleId;
        public double latitude;
        public double longitude;
        public int batteryPercent;
        public double temperatureC;
        public boolean helmetPresent;
        public boolean movementDetected;
        public boolean fault;
    }

    // --- Helpers ---
    private static <T> T readJson(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return mapper.readValue(is, clazz);
        }
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
