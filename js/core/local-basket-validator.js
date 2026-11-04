"use strict";

const { localBasketValidation: runRuleEngine } = require("../basket_validation");
const { normalizeCouponInput } = require("../lib/validation");

/**
 * Runs basket validation without making any TCB API calls.
 * Every coupon must already contain its purchase requirement.
 */
function localBasketValidation(input) {
    validateInputContainer(input);

    const coupons = input.coupons.map((coupon, index) => normalizeCouponInput(coupon, index));
    coupons.forEach((coupon, index) => {
        validateLocalCoupon(coupon, index);
    });

    logInput(input, Boolean(input.enable_logging));
    return runRuleEngine({ basket: structuredClone(input.basket), coupons });
}

function validateInputContainer(input) {
    if (!input || !Array.isArray(input.basket) || !Array.isArray(input.coupons)) {
        throw new TypeError("basket and coupons are required arrays.");
    }
}

function validateLocalCoupon(coupon, index) {
    if (typeof coupon.gs1 !== "string" || !coupon.gs1.trim()) {
        throw couponError(index, "coupon gs1 is required");
    }
    if (!coupon.purchase_requirement || typeof coupon.purchase_requirement !== "object") {
        throw couponError(
            index,
            "coupon purchase_requirement is required for localBasketValidation"
        );
    }
}

function couponError(index, message) {
    const error = new TypeError(message);
    error.code = "INVALID_COUPON_INPUT";
    error.details = { coupon_index: index };
    return error;
}

function logInput(input, enabled) {
    if (!enabled) return;
    console.log("[LocalBasketValidator] Validation input:");
    console.log(JSON.stringify(input, null, 2));
}

module.exports = { localBasketValidation };
