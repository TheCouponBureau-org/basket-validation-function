package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TcbTokenService {

    private static final Duration TOKEN_REUSE_WINDOW = Duration.ofHours(23);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CACHE_FILE = Paths.get(
            System.getProperty("java.io.tmpdir"),
            ".tcb-basket-validator",
            "access-token-cache.json");

    public static synchronized String getAccessToken(
            String baseUrl,
            String accessKey,
            String secretKey) {

        validateInputs(baseUrl, accessKey, secretKey);

        TokenCache cache = readCache();
        String secretKeyHash = sha256(secretKey);
        long nowEpochMs = Instant.now().toEpochMilli();

        for (TokenCacheEntry entry : cache.entries) {
            if (!baseUrl.equals(entry.baseUrl)) {
                continue;
            }

            if (!accessKey.equals(entry.accessKey)) {
                continue;
            }

            if (!secretKeyHash.equals(entry.secretKeyHash)) {
                continue;
            }

            long ageMs = nowEpochMs - entry.createdAtEpochMs;

            if (ageMs >= 0 && ageMs < TOKEN_REUSE_WINDOW.toMillis()) {
                return entry.accessToken;
            }
        }

        String accessToken = requestNewAccessToken(baseUrl, accessKey, secretKey);
        upsertCacheEntry(cache, baseUrl, accessKey, secretKeyHash, accessToken, nowEpochMs);
        writeCache(cache);

        return accessToken;
    }

    private static void validateInputs(
            String baseUrl,
            String accessKey,
            String secretKey) {

        if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalArgumentException(
                    "TCB token resolution requires tcbBaseUrl, tcbAccessKey, and tcbSecretKey.");
        }
    }

    private static String requestNewAccessToken(
            String baseUrl,
            String accessKey,
            String secretKey) {

        try {
            TokenRequest payload = new TokenRequest();
            payload.accessKey = accessKey;
            payload.secretKey = secretKey;

            HttpRequest request = TcbApiService.buildPostJsonRequest(
                    normalizeBaseUrl(baseUrl) + "/access_token",
                    accessKey,
                    null,
                    MAPPER.writeValueAsString(payload));

            HttpResponse<String> response =
                    TcbApiService.sendWithRetry(request, "access_token");

            TokenResponse tokenResponse =
                    MAPPER.readValue(response.body(), TokenResponse.class);

            if (tokenResponse == null || isBlank(tokenResponse.accessToken)) {
                throw new IllegalStateException(
                        "TCB access_token response did not contain x-access-token.");
            }

            return tokenResponse.accessToken;

        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fetch TCB access token.", exception);
        }
    }

    private static void upsertCacheEntry(
            TokenCache cache,
            String baseUrl,
            String accessKey,
            String secretKeyHash,
            String accessToken,
            long createdAtEpochMs) {

        cache.entries.removeIf(entry ->
                baseUrl.equals(entry.baseUrl)
                        && accessKey.equals(entry.accessKey)
                        && secretKeyHash.equals(entry.secretKeyHash));

        TokenCacheEntry entry = new TokenCacheEntry();
        entry.baseUrl = baseUrl;
        entry.accessKey = accessKey;
        entry.secretKeyHash = secretKeyHash;
        entry.accessToken = accessToken;
        entry.createdAtEpochMs = createdAtEpochMs;
        cache.entries.add(entry);
    }

    private static TokenCache readCache() {
        if (!Files.exists(CACHE_FILE)) {
            return new TokenCache();
        }

        try {
            return MAPPER.readValue(CACHE_FILE.toFile(), TokenCache.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read local TCB token cache.", exception);
        }
    }

    private static void writeCache(TokenCache cache) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(CACHE_FILE.toFile(), cache);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write local TCB token cache.", exception);
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();

            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }

            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        public String status;
        @com.fasterxml.jackson.annotation.JsonProperty("x-access-token")
        public String accessToken;
    }

    private static class TokenRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("access_key")
        public String accessKey;
        @com.fasterxml.jackson.annotation.JsonProperty("secret_key")
        public String secretKey;
    }

    private static class TokenCache {
        public List<TokenCacheEntry> entries = new ArrayList<>();
    }

    private static class TokenCacheEntry {
        public String baseUrl;
        public String accessKey;
        public String secretKeyHash;
        public String accessToken;
        public long createdAtEpochMs;
    }
}
