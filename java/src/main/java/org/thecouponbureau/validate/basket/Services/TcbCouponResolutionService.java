package org.thecouponbureau.validate.basket.Services;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;

public class TcbCouponResolutionService {

    private static final int COUPON_GS1_LENGTH = 34;
    private static final int REDEEM_BATCH_SIZE = 15;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static List<Coupon> resolveCoupons(
            String baseUrl,
            String accessKey,
            String accessToken,
            List<Coupon> coupons) {

        if (coupons == null || coupons.isEmpty()) {
            return coupons;
        }

        Map<Integer, List<ResolvedCouponItem>> resolvedCouponsByOriginalIndex = new HashMap<>();
        Map<Integer, Boolean> attemptedResolutionByOriginalIndex = new HashMap<>();
        List<CouponBucket> buckets = buildBuckets(coupons);

        if (buckets.isEmpty()) {
            return new ArrayList<>(coupons);
        }

        for (CouponBucket bucket : buckets) {
            for (Integer originalIndex : bucket.originalIndexes()) {
                attemptedResolutionByOriginalIndex.put(originalIndex, true);
            }
        }

        List<CompletableFuture<BucketResolution>> futures = new ArrayList<>();

        for (CouponBucket bucket : buckets) {
            futures.add(requestRedeemAsync(baseUrl, accessKey, accessToken, bucket));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<BucketResolution> future : futures) {
            BucketResolution bucketResolution = future.join();

            for (Map.Entry<Integer, List<ResolvedCouponItem>> entry
                    : bucketResolution.resolvedCouponsByOriginalIndex.entrySet()) {

                resolvedCouponsByOriginalIndex
                        .computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
                        .addAll(entry.getValue());
            }
        }

        List<Coupon> flattenedCoupons = new ArrayList<>();

        for (int index = 0; index < coupons.size(); index++) {
            List<ResolvedCouponItem> resolvedItems =
                    resolvedCouponsByOriginalIndex.get(index);

            if (resolvedItems == null || resolvedItems.isEmpty()) {
                if (Boolean.TRUE.equals(attemptedResolutionByOriginalIndex.get(index))) {
                    continue;
                }
                flattenedCoupons.add(coupons.get(index));
                continue;
            }

            Map<String, Integer> inputOrderByGs1 =
                    buildInputOrderMap(expandRequestedGs1s(coupons.get(index).gs1));

            resolvedItems.sort(
                    Comparator.comparingInt(
                                    (ResolvedCouponItem item) ->
                                            resolveSortOrder(item, inputOrderByGs1))
                            .thenComparingInt(item -> item.sequence));

            for (ResolvedCouponItem item : resolvedItems) {
                flattenedCoupons.add(item.coupon);
            }
        }

        return flattenedCoupons;
    }

    private static List<CouponBucket> buildBuckets(List<Coupon> coupons) {
        List<CouponBucket> buckets = new ArrayList<>();
        List<RequestedCouponGs1> groupedCoupons = new ArrayList<>();

        for (int index = 0; index < coupons.size(); index++) {
            Coupon coupon = coupons.get(index);

            if (!needsResolution(coupon)) {
                continue;
            }

            CouponRef ref = new CouponRef(index, coupon);
            List<String> requestedGs1s = expandRequestedGs1s(coupon.gs1);

            if (coupon.gs1.length() == 16) {
                buckets.add(new CouponBucket(List.of(ref), new ArrayList<>(), true));
                continue;
            }

            for (int sequence = 0; sequence < requestedGs1s.size(); sequence++) {
                groupedCoupons.add(new RequestedCouponGs1(
                        ref,
                        requestedGs1s.get(sequence),
                        sequence));
            }
        }

        for (int start = 0; start < groupedCoupons.size(); start += REDEEM_BATCH_SIZE) {
            int end = Math.min(start + REDEEM_BATCH_SIZE, groupedCoupons.size());
            buckets.add(new CouponBucket(
                    new ArrayList<>(),
                    new ArrayList<>(groupedCoupons.subList(start, end)),
                    false));
        }

        return buckets;
    }

    private static List<String> expandRequestedGs1s(String gs1) {
        List<String> expandedGs1s = new ArrayList<>();

        if (isBlank(gs1)) {
            return expandedGs1s;
        }

        if (gs1.length() > 16 && gs1.length() % COUPON_GS1_LENGTH == 0) {
            for (int index = 0; index < gs1.length(); index += COUPON_GS1_LENGTH) {
                expandedGs1s.add(gs1.substring(index, index + COUPON_GS1_LENGTH));
            }
            return expandedGs1s;
        }

        expandedGs1s.add(gs1);
        return expandedGs1s;
    }

    private static boolean needsResolution(Coupon coupon) {
        return coupon != null
                && !isBlank(coupon.gs1)
                && coupon.baseGs1 == null
                && coupon.purchaseRequirement == null;
    }

    private static CompletableFuture<BucketResolution> requestRedeemAsync(
            String baseUrl,
            String accessKey,
            String accessToken,
            CouponBucket bucket) {

        try {
            RedeemRequest payload = new RedeemRequest();

            if (bucket.singleCouponBucket) {
                payload.gs1s.add(bucket.couponRefs.get(0).coupon.gs1);
            } else {
                for (RequestedCouponGs1 requestedCouponGs1 : bucket.requestedCouponGs1s) {
                    payload.gs1s.add(requestedCouponGs1.gs1);
                }
            }

            HttpRequest request = TcbApiService.buildPostJsonRequest(
                    normalizeBaseUrl(baseUrl) + "/retailer/redeem",
                    accessKey,
                    accessToken,
                    MAPPER.writeValueAsString(payload));

            return CompletableFuture.supplyAsync(() ->
                    TcbApiService.sendWithRetry(request, "retailer/redeem"))
                    .thenApply(response -> parseResolutionResponse(response, bucket));

        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize TCB redeem request.", exception);
        }
    }

    private static BucketResolution parseResolutionResponse(
            HttpResponse<String> response,
            CouponBucket bucket) {

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "TCB retailer/redeem request failed with HTTP " + response.statusCode());
        }

        try {
            RedeemResponse redeemResponse =
                    MAPPER.readValue(response.body(), RedeemResponse.class);

            if (redeemResponse.newlyRedeemed == null || redeemResponse.newlyRedeemed.isEmpty()) {
                return new BucketResolution(new HashMap<>());
            }

            if (redeemResponse.masterOfferFiles == null || redeemResponse.masterOfferFiles.isEmpty()) {
                return new BucketResolution(new HashMap<>());
            }

            Map<Integer, List<ResolvedCouponItem>> resolvedByIndex = new HashMap<>();

            if (bucket.singleCouponBucket) {
                CouponRef ref = bucket.couponRefs.get(0);
                List<ResolvedCouponItem> resolvedCoupons = new ArrayList<>();

                for (int sequence = 0; sequence < redeemResponse.newlyRedeemed.size(); sequence++) {
                    Coupon resolvedCoupon = toResolvedCoupon(
                            redeemResponse.newlyRedeemed.get(sequence),
                            redeemResponse.masterOfferFiles);

                    if (resolvedCoupon != null) {
                        resolvedCoupons.add(new ResolvedCouponItem(sequence, resolvedCoupon));
                    }
                }

                if (!resolvedCoupons.isEmpty()) {
                    resolvedByIndex.put(ref.index, resolvedCoupons);
                }
                return new BucketResolution(resolvedByIndex);
            }

            Map<String, RedeemedCoupon> redeemedByGs1 = new HashMap<>();

            for (RedeemedCoupon redeemedCoupon : redeemResponse.newlyRedeemed) {
                redeemedByGs1.put(redeemedCoupon.gs1, redeemedCoupon);
            }

            for (RequestedCouponGs1 requestedCouponGs1 : bucket.requestedCouponGs1s) {
                RedeemedCoupon redeemedCoupon = redeemedByGs1.get(requestedCouponGs1.gs1);

                if (redeemedCoupon == null) {
                    continue;
                }

                Coupon resolvedCoupon =
                        toResolvedCoupon(redeemedCoupon, redeemResponse.masterOfferFiles);

                if (resolvedCoupon == null) {
                    continue;
                }

                resolvedByIndex
                        .computeIfAbsent(requestedCouponGs1.couponRef.index, unused -> new ArrayList<>())
                        .add(new ResolvedCouponItem(
                                requestedCouponGs1.sequence,
                                resolvedCoupon));
            }

            return new BucketResolution(resolvedByIndex);

        } catch (IOException exception) {
            throw new CompletionException(
                    new IllegalStateException("Unable to parse TCB redeem response.", exception));
        }
    }

    private static Coupon toResolvedCoupon(
            RedeemedCoupon redeemedCoupon,
            Map<String, PurchaseRequirement> masterOfferFiles) {

        if (redeemedCoupon == null
                || isBlank(redeemedCoupon.gs1)
                || isBlank(redeemedCoupon.masterOfferFile)) {
            return null;
        }

        PurchaseRequirement purchaseRequirement =
                masterOfferFiles.get(redeemedCoupon.masterOfferFile);

        if (purchaseRequirement == null) {
            return null;
        }

        Coupon resolvedCoupon = new Coupon();
        resolvedCoupon.gs1 = redeemedCoupon.gs1;
        resolvedCoupon.baseGs1 = redeemedCoupon.masterOfferFile;
        resolvedCoupon.purchaseRequirement = purchaseRequirement;
        return resolvedCoupon;
    }

    private static Map<String, Integer> buildInputOrderMap(List<String> requestedGs1s) {
        Map<String, Integer> inputOrderByGs1 = new HashMap<>();

        for (int index = 0; index < requestedGs1s.size(); index++) {
            inputOrderByGs1.putIfAbsent(requestedGs1s.get(index), index);
        }

        return inputOrderByGs1;
    }

    private static int resolveSortOrder(
            ResolvedCouponItem item,
            Map<String, Integer> inputOrderByGs1) {

        if (item == null || item.coupon == null || isBlank(item.coupon.gs1)) {
            return Integer.MAX_VALUE;
        }

        return inputOrderByGs1.getOrDefault(item.coupon.gs1, item.sequence);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (isBlank(baseUrl)) {
            throw new IllegalArgumentException("tcbBaseUrl is required for TCB coupon resolution.");
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class CouponRef {
        private final int index;
        private final Coupon coupon;

        private CouponRef(int index, Coupon coupon) {
            this.index = index;
            this.coupon = coupon;
        }
    }

    private static class CouponBucket {
        private final List<CouponRef> couponRefs;
        private final List<RequestedCouponGs1> requestedCouponGs1s;
        private final boolean singleCouponBucket;

        private CouponBucket(
                List<CouponRef> couponRefs,
                List<RequestedCouponGs1> requestedCouponGs1s,
                boolean singleCouponBucket) {
            this.couponRefs = couponRefs;
            this.requestedCouponGs1s = requestedCouponGs1s;
            this.singleCouponBucket = singleCouponBucket;
        }

        private List<Integer> originalIndexes() {
            List<Integer> indexes = new ArrayList<>();

            for (CouponRef couponRef : couponRefs) {
                indexes.add(couponRef.index);
            }

            for (RequestedCouponGs1 requestedCouponGs1 : requestedCouponGs1s) {
                if (!indexes.contains(requestedCouponGs1.couponRef.index)) {
                    indexes.add(requestedCouponGs1.couponRef.index);
                }
            }

            return indexes;
        }
    }

    private static class BucketResolution {
        private final Map<Integer, List<ResolvedCouponItem>> resolvedCouponsByOriginalIndex;

        private BucketResolution(Map<Integer, List<ResolvedCouponItem>> resolvedCouponsByOriginalIndex) {
            this.resolvedCouponsByOriginalIndex = resolvedCouponsByOriginalIndex;
        }
    }

    private static class RequestedCouponGs1 {
        private final CouponRef couponRef;
        private final String gs1;
        private final int sequence;

        private RequestedCouponGs1(CouponRef couponRef, String gs1, int sequence) {
            this.couponRef = couponRef;
            this.gs1 = gs1;
            this.sequence = sequence;
        }
    }

    private static class ResolvedCouponItem {
        private final int sequence;
        private final Coupon coupon;

        private ResolvedCouponItem(int sequence, Coupon coupon) {
            this.sequence = sequence;
            this.coupon = coupon;
        }
    }

    private static class RedeemRequest {
        public List<String> gs1s = new ArrayList<>();
        @JsonProperty("pre_process")
        public String preProcess = "yes";
        @JsonProperty("include_check_digit")
        public String includeCheckDigit = "yes";
        @JsonProperty("no_purchase_requirement")
        public String noPurchaseRequirement = "";
        public String offline = "";
        @JsonProperty("client_txn_id")
        public String clientTxnId = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RedeemResponse {
        @JsonProperty("newly_redeemed")
        public List<RedeemedCoupon> newlyRedeemed;
        @JsonProperty("master_offer_files")
        public Map<String, PurchaseRequirement> masterOfferFiles;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RedeemedCoupon {
        public String gs1;
        @JsonProperty("master_offer_file")
        public String masterOfferFile;
    }
}
