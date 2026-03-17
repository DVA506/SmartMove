package com.smartmove.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.Vehicle;
import com.smartmove.telemetry.TelemetryData;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

class SmartMoveApiServerTest {

    @Test
    void testRegisterVehicle() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = "{\"type\":\"E_SCOOTER\",\"city\":\"LONDON\"}";
        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleRegister(ex, controller);

        verify(controller).registerVehicle(any(Vehicle.class));
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testGetVehicleValid() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getRequestURI()).thenReturn(new URI("/vehicle?id=v123"));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);
        when(controller.getVehicle("v123")).thenReturn(Optional.of(new Vehicle()));

        SmartMoveApiServer.handleGet(ex, controller);

        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testStartRental() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = "{\"vehicleId\":\"v1\",\"city\":\"LONDON\"}";
        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleStart(ex, controller);

        verify(controller).startRental(eq("v1"), any());
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testEndRental() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = "{\"vehicleId\":\"v1\"}";
        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleEnd(ex, controller);

        verify(controller).endRental(eq("v1"));
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testHealthCheck() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);
        when(ex.getRequestMethod()).thenReturn("GET");

        SmartMoveApiServer.handleHealth(ex);

        verify(ex).sendResponseHeaders(eq(200), anyLong());
        assertTrue(os.toString().contains("ok"));
    }

    @Test
    void testOptionsPreflight() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleRegister(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testInvalidMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleRegister(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use POST"));
    }

    @Test
    void testGetVehicleNotFound() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getRequestURI()).thenReturn(new URI("/vehicle?id=nonexistent"));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);
        when(controller.getVehicle("nonexistent")).thenReturn(Optional.empty());

        SmartMoveApiServer.handleGet(ex, controller);

        verify(ex).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testGetVehicleMissingIdReturns400() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getRequestURI()).thenReturn(new URI("/vehicle"));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleGet(ex, controller);

        verify(ex).sendResponseHeaders(eq(400), anyLong());
        assertTrue(os.toString().contains("Missing id"));
    }

    @Test
    void testReserveVehicleSuccess() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = "{\"vehicleId\":\"v2\",\"city\":\"ROME\"}";
        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleReserve(ex, controller);

        verify(controller).reserveVehicle(eq("v2"), any());
        verify(ex).sendResponseHeaders(eq(200), anyLong());
        assertTrue(os.toString().contains("true"));
    }

    @Test
    void testReserveVehicleWrongMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleReserve(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use POST"));
    }

    @Test
    void testStartRentalWrongMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleStart(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use POST"));
    }

    @Test
    void testEndRentalWrongMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleEnd(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use POST"));
    }

    @Test
    void testTelemetrySuccess() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = """
                {
                  "vehicleId":"v9",
                  "latitude":41.9,
                  "longitude":12.5,
                  "batteryPercent":50,
                  "temperatureC":22.5,
                  "helmetPresent":true,
                  "movementDetected":false,
                  "fault":true
                }
                """;

        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleTelemetry(ex, controller);

        verify(controller).sendTelemetry(any(TelemetryData.class));
        verify(ex).sendResponseHeaders(eq(200), anyLong());
        assertTrue(os.toString().contains("queued"));
    }

    @Test
    void testTelemetryWrongMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleTelemetry(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use POST"));
    }

    @Test
    void testHealthOptionsReturns204() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleHealth(ex);

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testCorsHeadersAreSetOnRegister() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleRegister(ex, mock(SmartMoveCentralController.class));

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,OPTIONS", headers.getFirst("Access-Control-Allow-Methods"));
        assertEquals("Content-Type", headers.getFirst("Access-Control-Allow-Headers"));
    }

    @Test
    void testGetVehicleWrongMethodReturns405() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleGet(ex, controller);

        verify(ex).sendResponseHeaders(eq(405), anyLong());
        assertTrue(os.toString().contains("Use GET"));
    }

    @Test
    void testReserveOptionsReturns204() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleReserve(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testStartOptionsReturns204() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleStart(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testEndOptionsReturns204() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleEnd(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testTelemetryOptionsReturns204() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();

        when(ex.getRequestMethod()).thenReturn("OPTIONS");
        when(ex.getResponseHeaders()).thenReturn(headers);

        SmartMoveApiServer.handleTelemetry(ex, mock(SmartMoveCentralController.class));

        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testHandleGetSetsCorsHeaders() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getRequestURI()).thenReturn(new URI("/vehicle?id=v1"));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);
        when(controller.getVehicle("v1")).thenReturn(Optional.of(new Vehicle()));

        SmartMoveApiServer.handleGet(ex, controller);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,OPTIONS", headers.getFirst("Access-Control-Allow-Methods"));
        assertEquals("Content-Type", headers.getFirst("Access-Control-Allow-Headers"));
    }

    @Test
    void testTelemetryParsesFlagsAndReturnsQueued() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        SmartMoveCentralController controller = mock(SmartMoveCentralController.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        String json = """
                {
                  "vehicleId":"veh-1",
                  "latitude":10.0,
                  "longitude":20.0,
                  "batteryPercent":4,
                  "temperatureC":65.0,
                  "helmetPresent":true,
                  "movementDetected":true,
                  "fault":true
                }
                """;

        when(ex.getRequestMethod()).thenReturn("POST");
        when(ex.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleTelemetry(ex, controller);

        verify(controller).sendTelemetry(any(TelemetryData.class));
        verify(ex).sendResponseHeaders(eq(200), anyLong());
        assertTrue(os.toString().contains("queued"));
    }

    @Test
    void testHandleHealthSetsCorsHeaders() throws Exception {
        HttpExchange ex = mock(HttpExchange.class);
        Headers headers = new Headers();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        when(ex.getRequestMethod()).thenReturn("GET");
        when(ex.getResponseHeaders()).thenReturn(headers);
        when(ex.getResponseBody()).thenReturn(os);

        SmartMoveApiServer.handleHealth(ex);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,OPTIONS", headers.getFirst("Access-Control-Allow-Methods"));
        assertEquals("Content-Type", headers.getFirst("Access-Control-Allow-Headers"));
        assertTrue(os.toString().contains("ok"));
    }
}