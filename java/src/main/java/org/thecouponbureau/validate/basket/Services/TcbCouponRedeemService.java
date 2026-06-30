package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TcbCouponRedeemService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static String redeemCoupons(
            String baseUrl,
            String accessKey,
            String secretKey,
            List<String> gs1s) {

        if (gs1s == null || gs1s.isEmpty()) {
            throw new IllegalArgumentException("At least one gs1 is required for coupon redemption.");
        }

        String accessToken = TcbTokenService.getAccessToken(baseUrl, accessKey, secretKey);
        return redeemCouponsWithAccessToken(baseUrl, accessKey, accessToken, gs1s);
    }

    public static String redeemCouponsWithAccessToken(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> gs1s) {

        validateInputs(baseUrl, accessKey, accessToken, gs1s);

        try {
            RedeemRequest payload = new RedeemRequest();
            payload.gs1s = new ArrayList<>(gs1s);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/retailer/redeem"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", accessKey)
                    .header("x-access-token", accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "TCB retailer/redeem request failed with HTTP " + response.statusCode());
            }

            return response.body();

        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalStateException("Unable to redeem coupons through TCB retailer/redeem.", exception);
        }
    }

    private static void validateInputs(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> gs1s) {

        if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(accessToken)) {
            throw new IllegalArgumentException(
                    "TCB redeem requires baseUrl, accessKey, and accessToken.");
        }

        if (gs1s == null || gs1s.isEmpty()) {
            throw new IllegalArgumentException("At least one gs1 is required for coupon redemption.");
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class RedeemRequest {
        public List<String> gs1s = new ArrayList<>();
        @JsonProperty("include_check_digit")
        public String includeCheckDigit = "yes";
        @JsonProperty("no_purchase_requirement")
        public String noPurchaseRequirement = "";
        public String offline = "";
        @JsonProperty("client_txn_id")
        public String clientTxnId = "";
    }
}
