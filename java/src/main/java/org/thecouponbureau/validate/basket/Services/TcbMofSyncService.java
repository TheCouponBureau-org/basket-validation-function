package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;

public class TcbMofSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final long[] RETRY_BACKOFF_MS = new long[] {10000L, 20000L};
    private static final String MODE_INITIAL = "initial";
    private static final String MODE_INCREMENTAL = "incremental";

    /**
     * Pulls MOF purchase requirements from TCB and maps them into SDK-native
     * purchase requirement objects.
     *
     * <p>Mode behavior:
     * <ul>
     *   <li>{@code initial}: last 6 months through tomorrow</li>
     *   <li>{@code incremental}: yesterday through tomorrow</li>
     * </ul>
     *
     * <p>The incremental mode assumption is intentionally simple because the
     * caller contract does not provide a custom from-date watermark.
     */
    public static SyncMofResponse syncMasterOfferFiles(
            String baseUrl,
            String accessKey,
            String accessToken,
            String mode,
            String pageNo) {
        return syncMasterOfferFiles(
                baseUrl,
                accessKey,
                accessToken,
                mode,
                pageNo,
                Clock.systemUTC());
    }

    static SyncMofResponse syncMasterOfferFiles(
            String baseUrl,
            String accessKey,
            String accessToken,
            String mode,
            String pageNo,
            Clock clock) {

        validateInputs(baseUrl, accessKey, accessToken, mode);

        DateRange dateRange = resolveDateRange(mode, LocalDate.now(clock));
        String url = buildSyncUrl(baseUrl, dateRange, pageNo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", accessKey)
                .header("x-access-token", accessToken)
                .GET()
                .build();

        HttpResponse<String> response = sendWithRetryFor5xx(request, "syncmof");

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "TCB syncmof request failed with HTTP " + response.statusCode());
        }

        try {
            ApiResponse apiResponse = MAPPER.readValue(response.body(), ApiResponse.class);
            SyncMofResponse mappedResponse = new SyncMofResponse();
            mappedResponse.status = apiResponse.status;
            mappedResponse.nextPageNo = apiResponse.nextPageNo;
            mappedResponse.executionId = apiResponse.executionId;
            mappedResponse.executionTimeInMs = apiResponse.executionTimeInMs;
            mappedResponse.executionStartTime = apiResponse.executionStartTime;
            mappedResponse.data = mapRecords(apiResponse.data);
            return mappedResponse;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse TCB syncmof response.", exception);
        }
    }

    static DateRange resolveDateRange(String mode, LocalDate currentDate) {
        if (MODE_INITIAL.equalsIgnoreCase(mode)) {
            return new DateRange(currentDate.minusMonths(6), currentDate.plusDays(1));
        }

        if (MODE_INCREMENTAL.equalsIgnoreCase(mode)) {
            return new DateRange(currentDate.minusDays(1), currentDate.plusDays(1));
        }

        throw new IllegalArgumentException("mode must be either initial or incremental.");
    }

    static List<MasterOfferFileRecord> mapRecords(List<ApiRecord> apiRecords) {
        List<MasterOfferFileRecord> records = new ArrayList<>();

        if (apiRecords == null) {
            return records;
        }

        for (ApiRecord apiRecord : apiRecords) {
            if (apiRecord == null || isBlank(apiRecord.baseGs1)) {
                continue;
            }

            MasterOfferFileRecord record = new MasterOfferFileRecord();
            record.baseGs1 = apiRecord.baseGs1;

            PurchaseRequirement purchaseRequirement = new PurchaseRequirement();
            purchaseRequirement.primaryPurchaseGtins = apiRecord.primaryPurchaseGtins;
            purchaseRequirement.primaryPurchaseEans = apiRecord.primaryPurchaseEans;
            purchaseRequirement.excludedPrimaryPurchaseGtins = apiRecord.excludedPrimaryPurchaseGtins;
            purchaseRequirement.excludedPrimaryPurchaseEans = apiRecord.excludedPrimaryPurchaseEans;
            purchaseRequirement.primaryPurchasePrefixedCode = apiRecord.primaryPurchasePrefixedCode;
            purchaseRequirement.excludedPrimaryPurchasePrefixedCode =
                    apiRecord.excludedPrimaryPurchasePrefixedCode;
            purchaseRequirement.primaryPurchaseSaveValue = apiRecord.primaryPurchaseSaveValue;
            purchaseRequirement.primaryPurchaseRequirements = apiRecord.primaryPurchaseRequirements;
            purchaseRequirement.primaryPurchaseReqCode = apiRecord.primaryPurchaseReqCode;

            purchaseRequirement.secondPurchaseGtins = apiRecord.secondPurchaseGtins;
            purchaseRequirement.secondPurchaseEans = apiRecord.secondPurchaseEans;
            purchaseRequirement.excludedSecondPurchaseGtins = apiRecord.excludedSecondPurchaseGtins;
            purchaseRequirement.excludedSecondPurchaseEans = apiRecord.excludedSecondPurchaseEans;
            purchaseRequirement.secondPurchasePrefixedCode = apiRecord.secondPurchasePrefixedCode;
            purchaseRequirement.excludedSecondPurchasePrefixedCode =
                    apiRecord.excludedSecondPurchasePrefixedCode;
            purchaseRequirement.secondPurchaseSaveValue = apiRecord.secondPurchaseSaveValue;
            purchaseRequirement.secondPurchaseRequirements = apiRecord.secondPurchaseRequirements;
            purchaseRequirement.secondPurchaseReqCode = apiRecord.secondPurchaseReqCode;

            purchaseRequirement.thirdPurchaseGtins = apiRecord.thirdPurchaseGtins;
            purchaseRequirement.thirdPurchaseEans = apiRecord.thirdPurchaseEans;
            purchaseRequirement.excludedThirdPurchaseGtins = apiRecord.excludedThirdPurchaseGtins;
            purchaseRequirement.excludedThirdPurchaseEans = apiRecord.excludedThirdPurchaseEans;
            purchaseRequirement.thirdPurchasePrefixedCode = apiRecord.thirdPurchasePrefixedCode;
            purchaseRequirement.excludedThirdPurchasePrefixedCode =
                    apiRecord.excludedThirdPurchasePrefixedCode;
            purchaseRequirement.thirdPurchaseSaveValue = apiRecord.thirdPurchaseSaveValue;
            purchaseRequirement.thirdPurchaseRequirements = apiRecord.thirdPurchaseRequirements;
            purchaseRequirement.thirdPurchaseReqCode = apiRecord.thirdPurchaseReqCode;

            purchaseRequirement.saveValueCode = apiRecord.saveValueCode;
            purchaseRequirement.appliesToWhichItem = apiRecord.appliesToWhichItem;
            purchaseRequirement.additionalPurchaseRulesCode = apiRecord.additionalPurchaseRulesCode;

            record.purchaseRequirement = purchaseRequirement;
            records.add(record);
        }

        return records;
    }

    private static HttpResponse<String> sendWithRetryFor5xx(
            HttpRequest request,
            String operationName) {
        IOException lastIoException = null;
        int lastStatusCode = -1;

        for (int attempt = 0; attempt <= RETRY_BACKOFF_MS.length; attempt++) {
            try {
                HttpResponse<String> response =
                        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 500) {
                    return response;
                }

                lastStatusCode = response.statusCode();
            } catch (IOException exception) {
                lastIoException = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted during TCB API call for " + operationName + ".", exception);
            }

            if (attempt < RETRY_BACKOFF_MS.length) {
                sleep(RETRY_BACKOFF_MS[attempt], operationName);
            }
        }

        if (lastIoException != null) {
            throw new IllegalStateException(
                    "TCB API call failed for " + operationName + " after retries.",
                    lastIoException);
        }

        throw new IllegalStateException(
                "TCB API call failed for " + operationName
                        + " after retries with HTTP "
                        + lastStatusCode);
    }

    private static String buildSyncUrl(String baseUrl, DateRange dateRange, String pageNo) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String effectivePageNo = pageNo == null ? "" : pageNo;
        return normalizedBaseUrl
                + "/syncmof/"
                + dateRange.fromDate
                + "/"
                + dateRange.toDate
                + "/updated?pageNo="
                + effectivePageNo;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }

    private static void validateInputs(
            String baseUrl,
            String accessKey,
            String accessToken,
            String mode) {
        if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(accessToken) || isBlank(mode)) {
            throw new IllegalArgumentException(
                    "MOF sync requires baseUrl, accessKey, accessToken, and mode.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    public static class SyncMofResponse {
        public String status;
        public List<MasterOfferFileRecord> data = new ArrayList<>();
        public String nextPageNo;
        public String executionId;
        public Long executionTimeInMs;
        public Long executionStartTime;
    }

    public static class MasterOfferFileRecord {
        @JsonProperty("base_gs1")
        public String baseGs1;
        @JsonProperty("purchase_requirement")
        public PurchaseRequirement purchaseRequirement;
    }

    static class DateRange {
        final LocalDate fromDate;
        final LocalDate toDate;

        private DateRange(LocalDate fromDate, LocalDate toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiResponse {
        public String status;
        public List<ApiRecord> data;
        @JsonProperty("nextPageNo")
        public String nextPageNo;
        @JsonProperty("execution_id")
        public String executionId;
        @JsonProperty("execution_time_in_ms")
        public Long executionTimeInMs;
        @JsonProperty("execution_start_time")
        public Long executionStartTime;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiRecord {
        @JsonProperty("base_gs1")
        public String baseGs1;

        public List<String> primaryPurchaseGtins;
        public List<String> primaryPurchaseEans;
        public List<String> excludedPrimaryPurchaseGtins;
        public List<String> excludedPrimaryPurchaseEans;
        public Object primaryPurchasePrefixedCode;
        public Object excludedPrimaryPurchasePrefixedCode;
        public Long primaryPurchaseSaveValue;
        public Long primaryPurchaseRequirements;
        public Integer primaryPurchaseReqCode;

        public List<String> secondPurchaseGtins;
        public List<String> secondPurchaseEans;
        public List<String> excludedSecondPurchaseGtins;
        public List<String> excludedSecondPurchaseEans;
        public Object secondPurchasePrefixedCode;
        public Object excludedSecondPurchasePrefixedCode;
        public Long secondPurchaseSaveValue;
        public Long secondPurchaseRequirements;
        public Integer secondPurchaseReqCode;

        public List<String> thirdPurchaseGtins;
        public List<String> thirdPurchaseEans;
        public List<String> excludedThirdPurchaseGtins;
        public List<String> excludedThirdPurchaseEans;
        public Object thirdPurchasePrefixedCode;
        public Object excludedThirdPurchasePrefixedCode;
        public Long thirdPurchaseSaveValue;
        public Long thirdPurchaseRequirements;
        public Integer thirdPurchaseReqCode;

        public Integer saveValueCode;
        public Integer appliesToWhichItem;
        public Integer additionalPurchaseRulesCode;
    }
}
