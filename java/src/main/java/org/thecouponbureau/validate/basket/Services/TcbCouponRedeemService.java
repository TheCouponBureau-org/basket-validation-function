package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TcbCouponRedeemService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int REDEEM_CHUNK_SIZE = 15;

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

        List<List<String>> chunks = chunkGs1s(gs1s, REDEEM_CHUNK_SIZE);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (List<String> chunk : chunks) {
            futures.add(redeemChunkAsync(baseUrl, accessKey, accessToken, chunk));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> chunkResponses = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            chunkResponses.add(future.join());
        }

        return mergeRedeemResponses(chunkResponses);
    }

    static List<List<String>> chunkGs1s(List<String> gs1s, int chunkSize) {
        List<List<String>> chunks = new ArrayList<>();

        for (int index = 0; index < gs1s.size(); index += chunkSize) {
            int endIndex = Math.min(index + chunkSize, gs1s.size());
            chunks.add(new ArrayList<>(gs1s.subList(index, endIndex)));
        }

        return chunks;
    }

    static String mergeRedeemResponses(List<String> chunkResponses) {
        if (chunkResponses == null || chunkResponses.isEmpty()) {
            throw new IllegalArgumentException("At least one redeem response is required.");
        }

        try {
            ObjectNode merged = MAPPER.createObjectNode();
            ArrayNode mergedNewlyRedeemed = MAPPER.createArrayNode();
            ObjectNode mergedMasterOfferFiles = MAPPER.createObjectNode();
            ArrayNode executionIds = MAPPER.createArrayNode();
            LinkedHashSet<String> messages = new LinkedHashSet<>();
            LinkedHashSet<String> statusCodes = new LinkedHashSet<>();
            int totalProcessed = 0;
            long executionTimeInMs = 0L;
            Long earliestExecutionStartTime = null;
            Long latestEventTimestamp = null;
            String status = "success";
            String emailDomain = null;
            String preProcess = null;

            for (String chunkResponse : chunkResponses) {
                JsonNode root = MAPPER.readTree(chunkResponse);

                if (root.hasNonNull("status") && !"success".equals(root.get("status").asText())) {
                    status = root.get("status").asText();
                }

                if (root.hasNonNull("status_code")) {
                    statusCodes.add(root.get("status_code").asText());
                }

                if (root.hasNonNull("message")) {
                    messages.add(root.get("message").asText());
                }

                if (emailDomain == null && root.hasNonNull("emailDomain")) {
                    emailDomain = root.get("emailDomain").asText();
                }

                if (preProcess == null && root.hasNonNull("pre_process")) {
                    preProcess = root.get("pre_process").asText();
                }

                totalProcessed += root.path("total_gs1s_processed").asInt(0);
                executionTimeInMs += root.path("execution_time_in_ms").asLong(0L);

                if (root.hasNonNull("execution_start_time")) {
                    long executionStartTime = root.get("execution_start_time").asLong();
                    if (earliestExecutionStartTime == null || executionStartTime < earliestExecutionStartTime) {
                        earliestExecutionStartTime = executionStartTime;
                    }
                }

                if (root.hasNonNull("event_timestamp")) {
                    long eventTimestamp = root.get("event_timestamp").asLong();
                    if (latestEventTimestamp == null || eventTimestamp > latestEventTimestamp) {
                        latestEventTimestamp = eventTimestamp;
                    }
                }

                JsonNode executionId = root.get("execution_id");
                if (executionId != null && !executionId.isNull()) {
                    executionIds.add(executionId.asText());
                }

                JsonNode newlyRedeemed = root.get("newly_redeemed");
                if (newlyRedeemed != null && newlyRedeemed.isArray()) {
                    for (JsonNode item : newlyRedeemed) {
                        mergedNewlyRedeemed.add(item);
                    }
                }

                JsonNode masterOfferFiles = root.get("master_offer_files");
                if (masterOfferFiles != null && masterOfferFiles.isObject()) {
                    masterOfferFiles.fields().forEachRemaining(entry ->
                            mergedMasterOfferFiles.set(entry.getKey(), entry.getValue()));
                }
            }

            merged.put("status", status);
            merged.put("status_code", statusCodes.size() == 1
                    ? statusCodes.iterator().next()
                    : String.join(",", statusCodes));
            merged.set("newly_redeemed", mergedNewlyRedeemed);
            merged.put("total_gs1s_processed", totalProcessed);
            merged.put("message", messages.size() == 1
                    ? messages.iterator().next()
                    : "Redeemed " + mergedNewlyRedeemed.size() + " gs1(s)");

            if (emailDomain != null) {
                merged.put("emailDomain", emailDomain);
            }

            merged.set("master_offer_files", mergedMasterOfferFiles);

            if (executionIds.size() == 1) {
                merged.put("execution_id", executionIds.get(0).asText());
            } else {
                merged.set("execution_ids", executionIds);
            }

            merged.put("execution_time_in_ms", executionTimeInMs);

            if (earliestExecutionStartTime != null) {
                merged.put("execution_start_time", earliestExecutionStartTime);
            }

            if (latestEventTimestamp != null) {
                merged.put("event_timestamp", latestEventTimestamp);
            }

            if (preProcess != null) {
                merged.put("pre_process", preProcess);
            }

            return MAPPER.writeValueAsString(merged);

        } catch (IOException exception) {
            throw new IllegalStateException("Unable to merge TCB redeem responses.", exception);
        }
    }

    private static CompletableFuture<String> redeemChunkAsync(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> gs1s) {

        List<String> chunk = new ArrayList<>(gs1s);
        String clientTxnId = UUID.randomUUID().toString();

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        RedeemRequest payload = new RedeemRequest();
                        payload.gs1s = chunk;
                        payload.clientTxnId = clientTxnId;

                        HttpRequest request = TcbApiService.buildPostJsonRequest(
                                normalizeBaseUrl(baseUrl) + "/retailer/redeem",
                                accessKey,
                                accessToken,
                                MAPPER.writeValueAsString(payload));

                        HttpResponse<String> response =
                                TcbApiService.sendWithRetry(request, "retailer/redeem");

                        return response.body();

                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Unable to redeem coupons through TCB retailer/redeem.",
                                exception);
                    }
                })
                .exceptionally(exception -> {
                    throw new CompletionException(
                            new IllegalStateException(
                                    "Unable to redeem coupon chunk through TCB retailer/redeem.",
                                    exception));
                });
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
