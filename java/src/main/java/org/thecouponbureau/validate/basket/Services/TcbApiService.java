package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TcbApiService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final long[] RETRY_BACKOFF_MS = new long[] {50L, 100L, 200L};

    public static HttpResponse<String> sendWithRetry(
            HttpRequest request,
            String operationName) {

        int attempts = RETRY_BACKOFF_MS.length + 1;
        Exception lastException = null;
        int lastStatusCode = -1;

        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                HttpResponse<String> response =
                        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }

                lastStatusCode = response.statusCode();

            } catch (IOException exception) {
                lastException = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted during TCB API call for " + operationName + ".", exception);
            }

            if (attempt < RETRY_BACKOFF_MS.length) {
                sleep(RETRY_BACKOFF_MS[attempt], operationName);
            }
        }

        if (lastException != null) {
            throw new IllegalStateException(
                    "TCB API call failed for " + operationName + " after retries.",
                    lastException);
        }

        throw new IllegalStateException(
                "TCB API call failed for " + operationName
                        + " after retries with HTTP "
                        + lastStatusCode);
    }

    public static HttpRequest buildPostJsonRequest(
            String url,
            String accessKey,
            String accessToken,
            String requestBody) {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", accessKey);

        if (accessToken != null) {
            builder.header("x-access-token", accessToken);
        }

        return builder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
    }

    public static HttpRequest buildDeleteRequest(
            String url,
            String accessKey,
            String accessToken) {

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", accessKey)
                .header("x-access-token", accessToken)
                .DELETE()
                .build();
    }

    private static void sleep(long millis, String operationName) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted during retry backoff for " + operationName + ".", exception);
        }
    }
}
