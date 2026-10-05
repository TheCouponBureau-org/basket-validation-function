package org.thecouponbureau.validate.basket.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TcbModeIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> methods = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            methods.add(exchange.getRequestMethod());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"success\",\"newly_redeemed\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retailerRedemptionRemainsUnchangedForLegacyNullAndExplicitMode() throws Exception {
        TcbCouponRedeemService.redeemCoupons(baseUrl, "key", "token", List.of("gs1"));
        TcbCouponRedeemService.redeemCoupons(baseUrl, "key", "token", List.of("gs1"), null, "ignored");
        TcbCouponRedeemService.redeemCoupons(baseUrl, "key", "token", List.of("gs1"), "retailer", "ignored");
        assertEquals(List.of("/retailer/redeem", "/retailer/redeem", "/retailer/redeem"), paths);
        for (String body : bodies) {
            JsonNode payload = mapper.readTree(body);
            assertFalse(payload.has("retailer_email_domain"));
            assertFalse(payload.has("pre_process"));
            assertEquals("yes", payload.path("include_check_digit").asText());
            assertFalse(payload.path("client_txn_id").asText().isEmpty());
        }
    }

    @Test
    void acceleratorDomainIsSentInEveryRedemptionChunk() throws Exception {
        List<String> gs1s = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            gs1s.add("gs1-" + i);
        }
        TcbCouponRedeemService.redeemCoupons(baseUrl, "key", "token", gs1s, "accelerator", "retailer.example");
        assertEquals(3, paths.size());
        int total = 0;
        for (int i = 0; i < bodies.size(); i++) {
            assertEquals("/accelerator/redeem", paths.get(i));
            assertEquals("POST", methods.get(i));
            JsonNode payload = mapper.readTree(bodies.get(i));
            assertEquals("retailer.example", payload.path("retailer_email_domain").asText());
            assertTrue(payload.path("gs1s").size() <= 15);
            total += payload.path("gs1s").size();
        }
        assertEquals(31, total);
    }

    @Test
    void preprocessingCarriesDomainAndExistingFlags() throws Exception {
        Coupon coupon = new Coupon();
        coupon.gs1 = "1234567890123456";
        TcbCouponResolutionService.resolveCoupons(baseUrl, "key", "token", List.of(coupon),
                false, "accelerator", "retailer.example");
        TcbCouponResolutionService.validateCoupons(baseUrl, "key", "token", List.of(coupon),
                false, "accelerator", "retailer.example");
        TcbScannedGs1Service.parseScannedGs1s(baseUrl, "key", "token", List.of(coupon.gs1),
                "accelerator", "retailer.example");
        assertEquals(3, bodies.size());
        for (int i = 0; i < bodies.size(); i++) {
            assertEquals("/accelerator/redeem", paths.get(i));
            JsonNode payload = mapper.readTree(bodies.get(i));
            assertEquals("retailer.example", payload.path("retailer_email_domain").asText());
            assertEquals("yes", payload.path("pre_process").asText());
            assertEquals(i == 0 ? "" : "yes", payload.path("no_purchase_requirement").asText());
        }
    }

    @Test
    void rollbackRoutesByModeWithoutBodyOrDomain() {
        TcbCouponRollbackService.rollbackCoupons(baseUrl, "key", "token", List.of("gs1"));
        TcbCouponRollbackService.rollbackCoupons(baseUrl, "key", "token", List.of("gs1"), null);
        TcbCouponRollbackService.rollbackCoupons(baseUrl, "key", "token", List.of("gs1"), "retailer");
        TcbCouponRollbackService.rollbackCoupons(baseUrl, "key", "token", List.of("gs1/a"), "accelerator");
        assertEquals(List.of("/retailer/rollback/gs1", "/retailer/rollback/gs1",
                "/retailer/rollback/gs1", "/accelerator/rollback/gs1%2Fa"), paths);
        assertTrue(methods.stream().allMatch("DELETE"::equals));
        assertTrue(bodies.stream().allMatch(String::isEmpty));
    }

    @Test
    void rejectsMissingAcceleratorDomainAndUnknownModeBeforeSendingRequests() {
        for (String domain : new String[] {null, "", " "}) {
            assertThrows(IllegalArgumentException.class, () ->
                    TcbCouponRedeemService.redeemCoupons(baseUrl, "key", "token",
                            List.of("gs1"), "accelerator", domain));
        }
        assertThrows(IllegalArgumentException.class, () ->
                TcbCouponRollbackService.rollbackCoupons(baseUrl, "key", "token", List.of("gs1"), "invalid"));
        assertTrue(paths.isEmpty());
    }
}
