package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class TcbCouponRollbackService {

    public static Map<String, String> rollbackCoupons(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<String> gs1s) {

        validateInputs(baseUrl, accessKey, accessToken, gs1s);

        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();

        for (String gs1 : gs1s) {
            futures.add(rollbackCouponAsync(baseUrl, accessKey, accessToken, gs1));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, String> rollbackResponses = new LinkedHashMap<>();

        for (CompletableFuture<Map.Entry<String, String>> future : futures) {
            Map.Entry<String, String> entry = future.join();
            rollbackResponses.put(entry.getKey(), entry.getValue());
        }

        return rollbackResponses;
    }

    private static CompletableFuture<Map.Entry<String, String>> rollbackCouponAsync(
            String baseUrl,
            String accessKey,
            String accessToken,
            String gs1) {

        if (isBlank(gs1)) {
            throw new IllegalArgumentException("GS1 cannot be blank for rollback.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(
                        normalizeBaseUrl(baseUrl)
                                + "/retailer/rollback/"
                                + URLEncoder.encode(gs1, StandardCharsets.UTF_8)))
                .build();

        HttpRequest deleteRequest = TcbApiService.buildDeleteRequest(
                request.uri().toString(),
                accessKey,
                accessToken);

        return CompletableFuture.supplyAsync(() -> {
                    HttpResponse<String> response =
                            TcbApiService.sendWithRetry(
                                    deleteRequest,
                                    "retailer/rollback/" + gs1);
                    return Map.entry(gs1, response.body());
                })
                .exceptionally(exception -> {
                    throw new CompletionException(
                            new IllegalStateException(
                                    "Unable to rollback coupon for gs1 " + gs1,
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
                    "TCB rollback requires baseUrl, accessKey, and accessToken.");
        }

        if (gs1s == null || gs1s.isEmpty()) {
            throw new IllegalArgumentException("At least one gs1 is required for coupon rollback.");
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
}
