"use strict";

const { REDEEM_CHUNK_SIZE, SINGLE_FETCH_CODE_LENGTH } = require("../lib/constants");
const { chunk, expandRequestedGs1s, findMatchingRedeemedCoupon } = require("../lib/coupon-utils");
const { postJson } = require("../lib/tcb-api-client");
const { normalizeMode, resolveRetailerEmailDomain } = require("../lib/tcb-mode");
const { normalizeBaseUrl } = require("../lib/validation");

async function resolveCoupons(options) {
    return processCoupons({ ...options, includePurchaseRequirements: true });
}

async function validateCoupons(options) {
    return processCoupons({ ...options, includePurchaseRequirements: false });
}

async function processCoupons({
    baseUrl,
    accessKey,
    accessToken,
    coupons,
    enableLogging = false,
    includePurchaseRequirements,
    mode,
    retailerEmailDomain,
}) {
    if (!Array.isArray(coupons) || coupons.length === 0) {
        return coupons || [];
    }

    const requestMode = normalizeMode(mode);
    const emailDomain = resolveRetailerEmailDomain(requestMode, retailerEmailDomain);
    const buckets = buildBuckets(coupons);
    if (buckets.length === 0) {
        return [...coupons];
    }

    const resolutions = await Promise.all(
        buckets.map((bucket) =>
            requestBucket({
                baseUrl,
                accessKey,
                accessToken,
                bucket,
                enableLogging,
                includePurchaseRequirements,
                mode: requestMode,
                retailerEmailDomain: emailDomain,
            })
        )
    );

    const resolvedByIndex = new Map();
    const attemptedIndexes = new Set();
    for (const bucket of buckets) {
        for (const index of bucket.originalIndexes) attemptedIndexes.add(index);
    }
    for (const resolution of resolutions) {
        for (const [index, items] of resolution) {
            const existing = resolvedByIndex.get(index) || [];
            existing.push(...items);
            resolvedByIndex.set(index, existing);
        }
    }

    const flattened = [];
    for (let index = 0; index < coupons.length; index += 1) {
        const resolved = resolvedByIndex.get(index);
        if (!resolved?.length) {
            if (!attemptedIndexes.has(index)) flattened.push(coupons[index]);
            continue;
        }

        resolved.sort((left, right) => left.sequence - right.sequence);
        flattened.push(
            ...resolved.map((item) => ({
                ...item.coupon,
                _original_index: index,
                _resolved_sequence: item.sequence,
            }))
        );
    }

    if (enableLogging) {
        console.log("[TcbCouponResolutionService] Resolved coupons:");
        console.log(JSON.stringify(flattened, null, 2));
    }
    return flattened;
}

function buildBuckets(coupons) {
    const buckets = [];
    const grouped = [];

    coupons.forEach((coupon, index) => {
        if (!coupon?.gs1) return;

        if (coupon.gs1.length === SINGLE_FETCH_CODE_LENGTH) {
            buckets.push({ single: true, coupon, index, originalIndexes: [index] });
            return;
        }

        expandRequestedGs1s(coupon.gs1).forEach((gs1, sequence) => {
            grouped.push({ coupon, index, gs1, sequence });
        });
    });

    for (const requestedCoupons of chunk(grouped, REDEEM_CHUNK_SIZE)) {
        buckets.push({
            single: false,
            requestedCoupons,
            originalIndexes: [...new Set(requestedCoupons.map((item) => item.index))],
        });
    }
    return buckets;
}

async function requestBucket({
    baseUrl,
    accessKey,
    accessToken,
    bucket,
    enableLogging,
    includePurchaseRequirements,
    mode,
    retailerEmailDomain,
}) {
    const gs1s = bucket.single
        ? [bucket.coupon.gs1]
        : bucket.requestedCoupons.map((item) => item.gs1);
    const payload = {
        gs1s,
        pre_process: "yes",
        include_check_digit: "yes",
        no_purchase_requirement: includePurchaseRequirements ? "" : "yes",
        offline: "",
    };
    if (retailerEmailDomain) payload.retailer_email_domain = retailerEmailDomain;

    if (enableLogging) {
        console.log("[TcbCouponResolutionService] Redeem request:");
        console.log(JSON.stringify({ url: `${normalizeBaseUrl(baseUrl)}/retailer/redeem`, ...payload }, null, 2));
    }

    const response = await postJson(
        `${normalizeBaseUrl(baseUrl)}/retailer/redeem`,
        accessKey,
        accessToken,
        payload,
        `${mode}/redeem`
    );
    const body = await response.json();

    if (enableLogging) {
        console.log("[TcbCouponResolutionService] Redeem response:");
        console.log(JSON.stringify(body, null, 2));
    }

    return mapBucketResponse(bucket, body, includePurchaseRequirements);
}

function mapBucketResponse(bucket, response, includePurchaseRequirements) {
    const newlyRedeemed = Array.isArray(response?.newly_redeemed)
        ? response.newly_redeemed
        : [];
    const masterOfferFiles = response?.master_offer_files || {};
    const resolved = new Map();

    if (newlyRedeemed.length === 0) return resolved;
    if (includePurchaseRequirements && Object.keys(masterOfferFiles).length === 0) return resolved;

    if (bucket.single) {
        const items = newlyRedeemed
            .map((redeemedCoupon, sequence) => ({
                sequence,
                coupon: toResolvedCoupon(
                    redeemedCoupon,
                    masterOfferFiles,
                    bucket.coupon,
                    includePurchaseRequirements
                ),
            }))
            .filter((item) => item.coupon);
        if (items.length > 0) resolved.set(bucket.index, items);
        return resolved;
    }

    for (const requested of bucket.requestedCoupons) {
        const redeemedCoupon = findMatchingRedeemedCoupon(requested.gs1, newlyRedeemed);
        if (!redeemedCoupon) continue;

        const coupon = toResolvedCoupon(
            redeemedCoupon,
            masterOfferFiles,
            requested.coupon,
            includePurchaseRequirements
        );
        if (!coupon) continue;

        const items = resolved.get(requested.index) || [];
        items.push({ sequence: requested.sequence, coupon });
        resolved.set(requested.index, items);
    }
    return resolved;
}

function toResolvedCoupon(redeemedCoupon, masterOfferFiles, originalCoupon, includePurchaseRequirements) {
    if (!redeemedCoupon?.gs1 || !redeemedCoupon?.master_offer_file) return null;

    const purchaseRequirement = includePurchaseRequirements
        ? resolvePurchaseRequirement(redeemedCoupon, masterOfferFiles)
        : originalCoupon?.purchase_requirement;
    if (!purchaseRequirement) return null;

    return {
        gs1: redeemedCoupon.gs1,
        base_gs1: redeemedCoupon.master_offer_file,
        purchase_requirement: purchaseRequirement,
        validated: true,
        _original_index: originalCoupon?._original_index,
        _resolved_sequence: originalCoupon?._resolved_sequence || 0,
    };
}

function resolvePurchaseRequirement(redeemedCoupon, masterOfferFiles) {
    const direct = masterOfferFiles[redeemedCoupon.master_offer_file];
    if (direct) return direct;

    for (const [baseGs1, purchaseRequirement] of Object.entries(masterOfferFiles)) {
        if (
            redeemedCoupon.gs1.startsWith(baseGs1) &&
            redeemedCoupon.master_offer_file.startsWith(baseGs1)
        ) {
            return purchaseRequirement;
        }
    }
    return undefined;
}

module.exports = {
    buildBuckets,
    mapBucketResponse,
    resolveCoupons,
    validateCoupons,
};
