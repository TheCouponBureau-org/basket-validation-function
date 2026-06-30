package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TcbCouponRedeemService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            payload.clientTxnId = UUID.randomUUID().toString();

            HttpRequest request = TcbApiService.buildPostJsonRequest(
                    normalizeBaseUrl(baseUrl) + "/retailer/redeem",
                    accessKey,
                    accessToken,
                    MAPPER.writeValueAsString(payload));

            HttpResponse<String> response =
                    TcbApiService.sendWithRetry(request, "retailer/redeem");

            return response.body();

        } catch (IOException exception) {
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
