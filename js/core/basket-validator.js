"use strict";

const { localBasketValidation } = require("./local-basket-validator");
const { resolveCoupons, validateCoupons } = require("../services/coupon-resolution-service");
const {
    normalizeCouponInput,
    requireTcbCredentials,
} = require("../lib/validation");

/**
 * Validates scanned coupons through TCB, resolves missing offer data, and then
 * applies eligible coupons locally in the same order supplied by the caller.
 */
async function validateBasketHelper(input) {
    validateInputContainer(input);

    const baseUrl = input.tcb_base_url;
    const accessKey = input.tcb_access_key;
    const accessToken = input.tcb_access_token;
    requireTcbCredentials(baseUrl, accessKey, accessToken, "Basket validation");

    const coupons = deduplicateCoupons(
        input.coupons.map((coupon, index) => ({
            ...normalizeCouponInput(coupon, index),
            _original_index: index,
            _resolved_sequence: 0,
        }))
    );
    validateCouponGs1s(coupons);
    logInput(input, Boolean(input.enable_logging));

    const preparedCoupons = await prepareCoupons({
        baseUrl,
        accessKey,
        accessToken,
        coupons,
        enableLogging: Boolean(input.enable_logging),
        mode: input.mode,
        retailerEmailDomain: input.retailer_email_domain,
    });

    return localBasketValidation({
        basket: input.basket,
        coupons: preparedCoupons.map(toPublicCoupon),
        enable_logging: input.enable_logging,
    });
}

async function prepareCoupons(options) {
    const alreadyValidated = [];
    const validationOnly = [];
    const fullResolution = [];

    for (const coupon of options.coupons) {
        if (coupon.validated === true) {
            alreadyValidated.push(coupon);
        } else if (coupon.purchase_requirement) {
            validationOnly.push(coupon);
        } else {
            fullResolution.push(coupon);
        }
    }

    const [validated, resolved] = await Promise.all([
        validateCoupons({ ...options, coupons: validationOnly }),
        resolveCoupons({ ...options, coupons: fullResolution }),
    ]);

    return [...alreadyValidated, ...validated, ...resolved].sort(compareCouponOrder);
}

function compareCouponOrder(left, right) {
    const indexDifference =
        (left._original_index ?? Number.MAX_SAFE_INTEGER) -
        (right._original_index ?? Number.MAX_SAFE_INTEGER);
    if (indexDifference !== 0) return indexDifference;
    return (
        (left._resolved_sequence ?? Number.MAX_SAFE_INTEGER) -
        (right._resolved_sequence ?? Number.MAX_SAFE_INTEGER)
    );
}

function deduplicateCoupons(coupons) {
    const seen = new Set();
    return coupons.filter((coupon) => {
        if (coupon.gs1?.startsWith("81122")) return true;
        if (seen.has(coupon.gs1)) return false;
        seen.add(coupon.gs1);
        return true;
    });
}

function toPublicCoupon(coupon) {
    return {
        gs1: coupon.gs1,
        purchase_requirement: coupon.purchase_requirement,
        validated: coupon.validated,
    };
}

function validateInputContainer(input) {
    if (!input || !Array.isArray(input.basket) || !Array.isArray(input.coupons)) {
        throw new TypeError("basket and coupons are required arrays.");
    }
}

function validateCouponGs1s(coupons) {
    coupons.forEach((coupon, index) => {
        if (typeof coupon.gs1 !== "string" || !coupon.gs1.trim()) {
            const error = new TypeError("coupon gs1 is required");
            error.code = "INVALID_COUPON_INPUT";
            error.details = { coupon_index: index };
            throw error;
        }
    });
}

function logInput(input, enabled) {
    if (!enabled) return;
    const redacted = {
        ...input,
        tcb_access_key: input.tcb_access_key ? "REDACTED" : input.tcb_access_key,
        tcb_access_token: input.tcb_access_token ? "REDACTED" : input.tcb_access_token,
    };
    console.log("[BasketValidator] Validation input:");
    console.log(JSON.stringify(redacted, null, 2));
}

module.exports = {
    compareCouponOrder,
    deduplicateCoupons,
    prepareCoupons,
    validateBasketHelper,
};
