"use strict";

function isBlank(value) {
    return typeof value !== "string" || value.trim().length === 0;
}

function requireTcbCredentials(baseUrl, accessKey, accessToken, operation) {
    if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(accessToken)) {
        throw new TypeError(`${operation} requires baseUrl, accessKey, and accessToken.`);
    }
}

function normalizeBaseUrl(baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
}

function normalizeCouponInput(coupon, index) {
    if (typeof coupon === "string") {
        return { gs1: coupon };
    }

    if (!coupon || typeof coupon !== "object" || Array.isArray(coupon)) {
        throw couponInputError(index, "coupon entry must be a gs1 string or coupon object");
    }

    const allowedFields = new Set(["gs1", "purchase_requirement", "validated", "base_gs1", "baseGs1"]);
    const invalidFields = Object.keys(coupon).filter((field) => !allowedFields.has(field));

    if (invalidFields.length > 0) {
        const error = couponInputError(
            index,
            "coupon input only supports gs1, optional purchase_requirement, and optional validated."
        );
        error.details.invalid_fields = invalidFields;
        throw error;
    }

    return {
        gs1: coupon.gs1,
        purchase_requirement: coupon.purchase_requirement,
        validated: coupon.validated,
    };
}

function couponInputError(index, message) {
    const error = new TypeError(message);
    error.code = "INVALID_COUPON_INPUT";
    error.details = { coupon_index: index };
    return error;
}

function buildValidationError(code, message, details) {
    return {
        discount_in_cents: 0,
        applied_coupons: [],
        error: { code, message, details: details || null },
    };
}

module.exports = {
    buildValidationError,
    couponInputError,
    isBlank,
    normalizeBaseUrl,
    normalizeCouponInput,
    requireTcbCredentials,
};
