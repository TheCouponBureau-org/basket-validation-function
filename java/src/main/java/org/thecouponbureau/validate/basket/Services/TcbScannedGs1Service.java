package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

public class TcbScannedGs1Service {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final int SERIALIZED_GS1_LENGTH = 34;
    private static final int BASE_GS1_LENGTH = 18;
    private static final int REDEEM_CHUNK_SIZE = 15;
    private static final int SINGLE_REDEEM_CODE_LENGTH = 16;
    private static final String CONSUMER_SERIALIZED_PREFIX = "8112";

    /**
     * Resolves scanned coupon values into serialized GS1 plus base GS1 pairs.
     *
     * <p>Serialized consumer coupons that already begin with {@code 8112} are
     * parsed locally. Only unresolved 16-digit fetch codes are sent to TCB,
     * with one redeem request per fetch code and all requests executed in
     * parallel. The TCB requests set {@code no_purchase_requirement=yes} to
     * keep the response lean because this API only returns GS1 and base GS1.
     */
    public static List<SerializedGs1Data> parseScannedGs1s(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> scannedGs1s) {

        validateInputs(baseUrl, accessKey, accessToken, scannedGs1s);

        List<SerializedGs1Data> resolvedGs1s = new ArrayList<>();
        List<List<String>> chunks = new ArrayList<>();
        for (String scannedGs1 : scannedGs1s) {
            if (isBlank(scannedGs1)) {
                continue;
            }

            String normalizedGs1 = scannedGs1.trim();
            List<SerializedGs1Data> locallyParsed = tryParseConsumerSerializedGs1s(normalizedGs1);

            if (!locallyParsed.isEmpty()) {
                resolvedGs1s.addAll(locallyParsed);
                continue;
            }

            if (normalizedGs1.length() == SINGLE_REDEEM_CODE_LENGTH) {
                chunks.add(List.of(normalizedGs1));
            }
        }

        if (chunks.isEmpty()) {
            return resolvedGs1s;
        }

        List<CompletableFuture<List<SerializedGs1Data>>> futures = new ArrayList<>();

        for (List<String> chunk : chunks) {
            futures.add(resolveChunkAsync(baseUrl, accessKey, accessToken, chunk));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<List<SerializedGs1Data>> future : futures) {
            resolvedGs1s.addAll(future.join());
        }

        return resolvedGs1s;
    }

    static List<List<String>> buildRedeemBatches(List<String> scannedGs1s) {
        List<List<String>> batches = new ArrayList<>();

        for (String scannedGs1 : scannedGs1s) {
            if (isBlank(scannedGs1)) {
                continue;
            }

            String normalizedGs1 = scannedGs1.trim();

            if (!tryParseConsumerSerializedGs1s(normalizedGs1).isEmpty()) {
                continue;
            }

            if (normalizedGs1.length() == SINGLE_REDEEM_CODE_LENGTH) {
                batches.add(List.of(normalizedGs1));
            }
        }
        return batches;
    }

    /**
     * Parses one serialized GS1 or a concatenated string of serialized GS1s
     * entirely locally when the value already matches the consumer coupon
     * format.
     */
    static List<SerializedGs1Data> tryParseConsumerSerializedGs1s(String scannedGs1) {
        List<SerializedGs1Data> parsedGs1s = new ArrayList<>();

        if (isBlank(scannedGs1)
                || scannedGs1.length() < SERIALIZED_GS1_LENGTH
                || scannedGs1.length() % SERIALIZED_GS1_LENGTH != 0
                || !isDigitsOnly(scannedGs1)) {
            return parsedGs1s;
        }

        for (int index = 0; index < scannedGs1.length(); index += SERIALIZED_GS1_LENGTH) {
            String serializedGs1 =
                    scannedGs1.substring(index, index + SERIALIZED_GS1_LENGTH);

            if (!serializedGs1.startsWith(CONSUMER_SERIALIZED_PREFIX)) {
                return new ArrayList<>();
            }

            SerializedGs1Data data = new SerializedGs1Data();
            data.gs1 = serializedGs1;
            data.baseGs1 = serializedGs1.substring(0, BASE_GS1_LENGTH);
            parsedGs1s.add(data);
        }

        return parsedGs1s;
    }

    static List<List<String>> chunkGs1s(List<String> gs1s, int chunkSize) {
        List<List<String>> chunks = new ArrayList<>();

        for (int index = 0; index < gs1s.size(); index += chunkSize) {
            int endIndex = Math.min(index + chunkSize, gs1s.size());
            chunks.add(new ArrayList<>(gs1s.subList(index, endIndex)));
        }

        return chunks;
    }

    static List<SerializedGs1Data> extractResolvedGs1s(String redeemResponseBody) {
        try {
            RedeemResponse redeemResponse =
                    MAPPER.readValue(redeemResponseBody, RedeemResponse.class);

            List<SerializedGs1Data> resolvedGs1s = new ArrayList<>();
            if (redeemResponse.newlyRedeemed == null) {
                return resolvedGs1s;
            }

            for (RedeemedCoupon redeemedCoupon : redeemResponse.newlyRedeemed) {
                if (redeemedCoupon == null
                        || isBlank(redeemedCoupon.gs1)
                        || isBlank(redeemedCoupon.masterOfferFile)) {
                    continue;
                }

                SerializedGs1Data data = new SerializedGs1Data();
                data.gs1 = redeemedCoupon.gs1;
                data.baseGs1 = redeemedCoupon.masterOfferFile;
                resolvedGs1s.add(data);
            }

            return resolvedGs1s;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse TCB redeem response.", exception);
        }
    }

    private static CompletableFuture<List<SerializedGs1Data>> resolveChunkAsync(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> scannedGs1s) {

        List<String> chunk = new ArrayList<>(scannedGs1s);

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        RedeemRequest payload = new RedeemRequest();
                        payload.gs1s = chunk;

                        HttpRequest request = TcbApiService.buildPostJsonRequest(
                                normalizeBaseUrl(baseUrl) + "/retailer/redeem",
                                accessKey,
                                accessToken,
                                MAPPER.writeValueAsString(payload));

                        HttpResponse<String> response =
                                TcbApiService.sendWithRetry(request, "retailer/redeem");

                        return extractResolvedGs1s(response.body());
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Unable to resolve scanned gs1s through TCB retailer/redeem.",
                                exception);
                    }
                })
                .exceptionally(exception -> {
                    throw new CompletionException(
                            new IllegalStateException(
                                    "Unable to resolve scanned gs1 chunk through TCB retailer/redeem.",
                                    exception));
                });
    }

    private static void validateInputs(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> scannedGs1s) {

        if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(accessToken)) {
            throw new IllegalArgumentException(
                    "TCB scanned gs1 parsing requires baseUrl, accessKey, and accessToken.");
        }

        if (scannedGs1s == null || scannedGs1s.isEmpty()) {
            throw new IllegalArgumentException("At least one scanned gs1 is required.");
        }
    }

    private static boolean isDigitsOnly(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }

        return true;
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

    public static class SerializedGs1Data {
        public String gs1;
        @JsonProperty("base_gs1")
        public String baseGs1;
    }

    private static class RedeemRequest {
        public List<String> gs1s = new ArrayList<>();
        @JsonProperty("pre_process")
        public String preProcess = "yes";
        @JsonProperty("include_check_digit")
        public String includeCheckDigit = "yes";
        @JsonProperty("no_purchase_requirement")
        public String noPurchaseRequirement = "yes";
        public String offline = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RedeemResponse {
        @JsonProperty("newly_redeemed")
        public List<RedeemedCoupon> newlyRedeemed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RedeemedCoupon {
        public String gs1;
        @JsonProperty("master_offer_file")
        public String masterOfferFile;
    }
}
