package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TcbTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String fetchAccessToken(
            String baseUrl,
            String accessKey,
            String secretKey) {

        validateInputs(baseUrl, accessKey, secretKey);

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

    private static void validateInputs(
            String baseUrl,
            String accessKey,
            String secretKey) {

        if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalArgumentException(
                    "TCB token fetch requires tcbBaseUrl, tcbAccessKey, and tcbSecretKey.");
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        public String status;
        @JsonProperty("x-access-token")
        public String accessToken;
    }

    private static class TokenRequest {
        @JsonProperty("access_key")
        public String accessKey;
        @JsonProperty("secret_key")
        public String secretKey;
    }
}
