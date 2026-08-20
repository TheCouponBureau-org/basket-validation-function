package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

public class TcbScannedGs1Service {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final int MIN_SERIALIZED_GS1_LENGTH = 34;
    private static final int MIN_BASE_GS1_LENGTH = 18;
    private static final int SERIAL_COMPONENT_LENGTH = 16;
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

        List<List<SerializedGs1Data>> resolvedByInputIndex = new ArrayList<>();
        for (int index = 0; index < scannedGs1s.size(); index++) {
            resolvedByInputIndex.add(new ArrayList<>());
        }

        List<PendingRedeemInput> redeemInputs = new ArrayList<>();

        for (int index = 0; index < scannedGs1s.size(); index++) {
            String scannedGs1 = scannedGs1s.get(index);
            if (isBlank(scannedGs1)) {
                continue;
            }

            String normalizedGs1 = scannedGs1.trim();
            List<SerializedGs1Data> locallyParsed = tryParseConsumerSerializedGs1s(normalizedGs1);

            if (!locallyParsed.isEmpty()) {
                resolvedByInputIndex.get(index).addAll(locallyParsed);
                continue;
            }

            redeemInputs.add(new PendingRedeemInput(index, normalizedGs1));
        }

        List<RedeemChunk> chunks = buildRedeemBatches(redeemInputs);

        if (chunks.isEmpty()) {
            return flattenResolvedGs1s(resolvedByInputIndex);
        }

        List<CompletableFuture<Map<Integer, List<SerializedGs1Data>>>> futures = new ArrayList<>();

        for (RedeemChunk chunk : chunks) {
            futures.add(resolveChunkAsync(baseUrl, accessKey, accessToken, chunk));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<Map<Integer, List<SerializedGs1Data>>> future : futures) {
            Map<Integer, List<SerializedGs1Data>> resolvedChunk = future.join();
            for (Map.Entry<Integer, List<SerializedGs1Data>> entry : resolvedChunk.entrySet()) {
                resolvedByInputIndex.get(entry.getKey()).addAll(entry.getValue());
            }
        }

        return flattenResolvedGs1s(resolvedByInputIndex);
    }

    static List<RedeemChunk> buildRedeemBatches(List<PendingRedeemInput> redeemInputs) {
        List<RedeemChunk> batches = new ArrayList<>();
        List<PendingRedeemInput> groupedRedeemCodes = new ArrayList<>();

        for (PendingRedeemInput redeemInput : redeemInputs) {
            if (redeemInput == null || isBlank(redeemInput.gs1)) {
                continue;
            }

            String normalizedGs1 = redeemInput.gs1.trim();

            if (!tryParseConsumerSerializedGs1s(normalizedGs1).isEmpty()) {
                continue;
            }

            if (normalizedGs1.length() == SINGLE_REDEEM_CODE_LENGTH) {
                batches.add(new RedeemChunk(List.of(redeemInput)));
                continue;
            }

            groupedRedeemCodes.add(redeemInput);
        }

        for (int index = 0; index < groupedRedeemCodes.size(); index += REDEEM_CHUNK_SIZE) {
            int endIndex = Math.min(index + REDEEM_CHUNK_SIZE, groupedRedeemCodes.size());
            batches.add(new RedeemChunk(new ArrayList<>(groupedRedeemCodes.subList(index, endIndex))));
        }

        return batches;
    }

    static int resolveBaseGs1Length(String serializedGs1, int startIndex) {
        int extensionDigitIndex = startIndex + 5;
        if (serializedGs1.length() <= extensionDigitIndex) {
            return -1;
        }

        char extensionDigit = serializedGs1.charAt(extensionDigitIndex);
        if (!Character.isDigit(extensionDigit)) {
            return -1;
        }

        return MIN_BASE_GS1_LENGTH + Character.getNumericValue(extensionDigit);
    }

    static int resolveSerializedGs1Length(String scannedGs1, int startIndex) {
        int baseGs1Length = resolveBaseGs1Length(scannedGs1, startIndex);
        if (baseGs1Length < 0) {
            return -1;
        }

        return baseGs1Length + SERIAL_COMPONENT_LENGTH;
    }

    /**
     * Parses one serialized GS1 or a concatenated string of serialized GS1s
     * entirely locally when the value already matches the consumer coupon
     * format.
     */
    static List<SerializedGs1Data> tryParseConsumerSerializedGs1s(String scannedGs1) {
        List<SerializedGs1Data> parsedGs1s = new ArrayList<>();

        if (isBlank(scannedGs1)
                || scannedGs1.length() < MIN_SERIALIZED_GS1_LENGTH
                || !isDigitsOnly(scannedGs1)) {
            return parsedGs1s;
        }

        for (int index = 0; index < scannedGs1.length();) {
            if (!scannedGs1.startsWith(CONSUMER_SERIALIZED_PREFIX, index)) {
                return new ArrayList<>();
            }

            int baseGs1Length = resolveBaseGs1Length(scannedGs1, index);
            int serializedGs1Length = resolveSerializedGs1Length(scannedGs1, index);

            if (baseGs1Length < 0
                    || serializedGs1Length < MIN_SERIALIZED_GS1_LENGTH
                    || index + serializedGs1Length > scannedGs1.length()) {
                return new ArrayList<>();
            }

            String serializedGs1 =
                    scannedGs1.substring(index, index + serializedGs1Length);


            SerializedGs1Data data = new SerializedGs1Data();
            data.gs1 = serializedGs1;
            data.baseGs1 = serializedGs1.substring(0, baseGs1Length);
            data.validated = false;
            parsedGs1s.add(data);
            index += serializedGs1Length;
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
                data.validated = true;
                resolvedGs1s.add(data);
            }

            return resolvedGs1s;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse TCB redeem response.", exception);
        }
    }

    private static List<SerializedGs1Data> flattenResolvedGs1s(
            List<List<SerializedGs1Data>> resolvedByInputIndex) {
        List<SerializedGs1Data> flattened = new ArrayList<>();

        for (List<SerializedGs1Data> resolvedItems : resolvedByInputIndex) {
            if (resolvedItems == null || resolvedItems.isEmpty()) {
                continue;
            }
            flattened.addAll(resolvedItems);
        }

        return flattened;
    }

    private static CompletableFuture<Map<Integer, List<SerializedGs1Data>>> resolveChunkAsync(
            String baseUrl,
            String accessKey,
            String accessToken,
            RedeemChunk redeemChunk) {

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        RedeemRequest payload = new RedeemRequest();
                        for (PendingRedeemInput redeemInput : redeemChunk.inputs) {
                            payload.gs1s.add(redeemInput.gs1);
                        }

                        HttpRequest request = TcbApiService.buildPostJsonRequest(
                                normalizeBaseUrl(baseUrl) + "/retailer/redeem",
                                accessKey,
                                accessToken,
                                MAPPER.writeValueAsString(payload));

                        HttpResponse<String> response =
                                TcbApiService.sendWithRetry(request, "retailer/redeem");

                        return extractResolvedGs1sByInput(response.body(), redeemChunk.inputs);
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

    static Map<Integer, List<SerializedGs1Data>> extractResolvedGs1sByInput(
            String redeemResponseBody,
            List<PendingRedeemInput> redeemInputs) {
        try {
            RedeemResponse redeemResponse =
                    MAPPER.readValue(redeemResponseBody, RedeemResponse.class);

            Map<Integer, List<SerializedGs1Data>> resolvedByInput = new HashMap<>();
            if (redeemResponse.newlyRedeemed == null || redeemInputs == null || redeemInputs.isEmpty()) {
                return resolvedByInput;
            }

            for (PendingRedeemInput redeemInput : redeemInputs) {
                List<SerializedGs1Data> matchedGs1s =
                        findMatchingResolvedGs1s(redeemInput.gs1, redeemResponse.newlyRedeemed);

                if (matchedGs1s.isEmpty()) {
                    continue;
                }

                resolvedByInput.put(redeemInput.inputIndex, matchedGs1s);
            }

            return resolvedByInput;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse TCB redeem response.", exception);
        }
    }

    static List<SerializedGs1Data> findMatchingResolvedGs1s(
            String requestedGs1,
            List<RedeemedCoupon> newlyRedeemed) {
        List<SerializedGs1Data> matches = new ArrayList<>();

        if (isBlank(requestedGs1) || newlyRedeemed == null || newlyRedeemed.isEmpty()) {
            return matches;
        }

        if (requestedGs1.length() == SINGLE_REDEEM_CODE_LENGTH) {
            for (RedeemedCoupon redeemedCoupon : newlyRedeemed) {
                if (redeemedCoupon == null
                        || isBlank(redeemedCoupon.gs1)
                        || isBlank(redeemedCoupon.masterOfferFile)) {
                    continue;
                }

                SerializedGs1Data data = new SerializedGs1Data();
                data.gs1 = redeemedCoupon.gs1;
                data.baseGs1 = redeemedCoupon.masterOfferFile;
                data.validated = true;
                matches.add(data);
            }

            return matches;
        }

        for (RedeemedCoupon redeemedCoupon : newlyRedeemed) {
            if (redeemedCoupon == null
                    || isBlank(redeemedCoupon.gs1)
                    || isBlank(redeemedCoupon.masterOfferFile)) {
                continue;
            }

            if (requestedGs1.equals(redeemedCoupon.gs1)) {
                SerializedGs1Data data = new SerializedGs1Data();
                data.gs1 = redeemedCoupon.gs1;
                data.baseGs1 = redeemedCoupon.masterOfferFile;
                data.validated = true;
                matches.add(data);
            }
        }

        if (!matches.isEmpty()) {
            return matches;
        }

        String requestedBaseGs1 = stripLastFourDigits(requestedGs1);
        if (isBlank(requestedBaseGs1)) {
            return matches;
        }

        for (RedeemedCoupon redeemedCoupon : newlyRedeemed) {
            if (redeemedCoupon == null
                    || isBlank(redeemedCoupon.gs1)
                    || isBlank(redeemedCoupon.masterOfferFile)) {
                continue;
            }

            if (requestedBaseGs1.equals(redeemedCoupon.masterOfferFile)) {
                SerializedGs1Data data = new SerializedGs1Data();
                data.gs1 = redeemedCoupon.gs1;
                data.baseGs1 = redeemedCoupon.masterOfferFile;
                data.validated = true;
                matches.add(data);
            }
        }

        return matches;
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

    private static String stripLastFourDigits(String gs1) {
        if (isBlank(gs1) || gs1.length() <= 4) {
            return null;
        }

        return gs1.substring(0, gs1.length() - 4);
    }

    public static class SerializedGs1Data {
        public String gs1;
        @JsonProperty("base_gs1")
        public String baseGs1;
        public Boolean validated;
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

    static class PendingRedeemInput {
        final int inputIndex;
        final String gs1;

        PendingRedeemInput(int inputIndex, String gs1) {
            this.inputIndex = inputIndex;
            this.gs1 = gs1;
        }
    }

    static class RedeemChunk {
        final List<PendingRedeemInput> inputs;

        RedeemChunk(List<PendingRedeemInput> inputs) {
            this.inputs = inputs;
        }
    }
}
