package com.smartmove.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmove.audit.AuditLogService;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.City;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleType;
import com.smartmove.storage.JsonPaymentStorage;
import com.smartmove.storage.JsonVehicleStorage;
import com.smartmove.storage.PaymentStorage;
import com.smartmove.storage.VehicleStorage;
import com.smartmove.telemetry.TelemetryData;
import com.smartmove.zones.JsonZoneRepository;
import com.smartmove.zones.ZoneRepository;
import com.smartmove.zones.ZoneService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

public class SmartMoveApiServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(SmartMoveApiServer.class.getName());

    private static final String OPTIONS = "OPTIONS";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String ERROR = "error";
    private static final String USE_POST = "Use POST";
    private static final String USE_GET = "Use GET";
    private static final String MISSING_ID = "Missing id";
    private static final String NOT_FOUND = "Not found";

    public static void main(String[] args) throws Exception {
        SmartMoveCentralController controller = buildController();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        registerContexts(server, controller);

        server.setExecutor(null);
        server.start();
        LOGGER.info("SmartMove API running on http://localhost:8080");

        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown));
    }

    private static SmartMoveCentralController buildController() {
        VehicleStorage vehicleStorage = new JsonVehicleStorage(Paths.get("data/vehicles.json"));
        AuditLogService audit = new AuditLogService(Paths.get("data/audit-log.jsonl"));
        ZoneRepository zoneRepo = new JsonZoneRepository(Paths.get("data/restricted-zones.json"));
        PaymentStorage paymentStorage = new JsonPaymentStorage(Paths.get("data/payments.json"));
        ZoneService zones = new ZoneService(zoneRepo);

        return new SmartMoveCentralController(vehicleStorage, audit, zones, paymentStorage);
    }

    private static void registerContexts(HttpServer server, SmartMoveCentralController controller) {
        server.createContext("/vehicles", ex -> handleCreateVehicle(ex, controller));
        server.createContext("/vehicle", ex -> handleGetVehicle(ex, controller));
        server.createContext("/reserve", ex -> handleReserve(ex, controller));
        server.createContext("/start", ex -> handleStartRental(ex, controller));
        server.createContext("/end", ex -> handleEndRental(ex, controller));
        server.createContext("/telemetry", ex -> handleTelemetry(ex, controller));
        server.createContext("/health", SmartMoveApiServer::handleHealth);
    }

    private static void handleCreateVehicle(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!POST.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_POST));
            return;
        }

        VehicleCreateRequest req = readJson(ex, VehicleCreateRequest.class);
        Vehicle vehicle = new Vehicle(req.getType(), req.getCity());
        controller.registerVehicle(vehicle);
        json(ex, 200, Map.of("id", vehicle.getId()));
    }

    private static void handleGetVehicle(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!GET.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_GET));
            return;
        }

        String id = extractId(ex);
        if (id == null || id.isBlank()) {
            json(ex, 400, Map.of(ERROR, MISSING_ID));
            return;
        }

        var vehicle = controller.getVehicle(id);
        if (vehicle.isEmpty()) {
            json(ex, 404, Map.of(ERROR, NOT_FOUND));
            return;
        }

        json(ex, 200, vehicle.get());
    }

    private static void handleReserve(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!POST.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_POST));
            return;
        }

        ActionRequest req = readJson(ex, ActionRequest.class);
        controller.reserveVehicle(req.getVehicleId(), req.getCity());
        json(ex, 200, Map.of("ok", true));
    }

    private static void handleStartRental(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!POST.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_POST));
            return;
        }

        ActionRequest req = readJson(ex, ActionRequest.class);
        controller.startRental(req.getVehicleId(), req.getCity());
        json(ex, 200, Map.of("ok", true));
    }

    private static void handleEndRental(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!POST.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_POST));
            return;
        }

        EndRequest req = readJson(ex, EndRequest.class);
        controller.endRental(req.getVehicleId());
        json(ex, 200, Map.of("ok", true));
    }

    private static void handleTelemetry(HttpExchange ex, SmartMoveCentralController controller) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        if (!POST.equals(ex.getRequestMethod())) {
            json(ex, 405, Map.of(ERROR, USE_POST));
            return;
        }

        TelemetryRequest req = readJson(ex, TelemetryRequest.class);
        TelemetryData telemetry = new TelemetryData(
                req.getVehicleId(),
                req.getLatitude(),
                req.getLongitude(),
                req.getBatteryPercent(),
                req.getTemperatureC()
        );
        telemetry.setHelmetPresent(req.isHelmetPresent());
        telemetry.setMovementDetected(req.isMovementDetected());
        telemetry.setFault(req.isFault());

        controller.sendTelemetry(telemetry);
        json(ex, 200, Map.of("queued", true));
    }

    private static void handleHealth(HttpExchange ex) throws IOException {
        cors(ex);
        if (isOptions(ex)) {
            sendNoContent(ex);
            return;
        }
        json(ex, 200, Map.of("status", "ok"));
    }

    public static class VehicleCreateRequest {
        private VehicleType type;
        private City city;

        public VehicleType getType() {
            return type;
        }

        public void setType(VehicleType type) {
            this.type = type;
        }

        public City getCity() {
            return city;
        }

        public void setCity(City city) {
            this.city = city;
        }
    }

    public static class ActionRequest {
        private String vehicleId;
        private City city;

        public String getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
        }

        public City getCity() {
            return city;
        }

        public void setCity(City city) {
            this.city = city;
        }
    }

    public static class EndRequest {
        private String vehicleId;

        public String getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
        }
    }

    public static class TelemetryRequest {
        private String vehicleId;
        private double latitude;
        private double longitude;
        private int batteryPercent;
        private double temperatureC;
        private boolean helmetPresent;
        private boolean movementDetected;
        private boolean fault;

        public String getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public int getBatteryPercent() {
            return batteryPercent;
        }

        public void setBatteryPercent(int batteryPercent) {
            this.batteryPercent = batteryPercent;
        }

        public double getTemperatureC() {
            return temperatureC;
        }

        public void setTemperatureC(double temperatureC) {
            this.temperatureC = temperatureC;
        }

        public boolean isHelmetPresent() {
            return helmetPresent;
        }

        public void setHelmetPresent(boolean helmetPresent) {
            this.helmetPresent = helmetPresent;
        }

        public boolean isMovementDetected() {
            return movementDetected;
        }

        public void setMovementDetected(boolean movementDetected) {
            this.movementDetected = movementDetected;
        }

        public boolean isFault() {
            return fault;
        }

        public void setFault(boolean fault) {
            this.fault = fault;
        }
    }

    private static <T> T readJson(HttpExchange ex, Class<T> clazz) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return MAPPER.readValue(is, clazz);
        }
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
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

    private static boolean isOptions(HttpExchange ex) {
        return OPTIONS.equals(ex.getRequestMethod());
    }

    private static void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    private static String extractId(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery();
        if (query != null && query.startsWith("id=")) {
            return query.substring(3);
        }
        return null;
    }
}