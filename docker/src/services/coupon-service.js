"use strict";

const { ClientInputError } = require("../http/errors");

function createCouponService({ sdk, tokenManager, config }) {
    async function redeem(coupons, context) {
        validateCoupons(coupons);
        const accessToken = await tokenManager.getAccessToken();
        return sdk.redeemCoupons(
            config.tcbEndpoint,
            config.tcbAccessKey,
            accessToken,
            coupons,
            context.mode,
            context.retailerEmailDomain
        );
    }

    async function rollback(coupons, context) {
        validateCoupons(coupons);
        const accessToken = await tokenManager.getAccessToken();
        return sdk.rollbackCoupons(
            config.tcbEndpoint,
            config.tcbAccessKey,
            accessToken,
            coupons,
            context.mode
        );
    }

    function validateCoupons(coupons) {
        if (!Array.isArray(coupons) || coupons.length === 0) {
            throw new ClientInputError("coupons must be a non-empty array of GS1 strings.");
        }
        if (coupons.some((coupon) => typeof coupon !== "string" || !coupon.trim())) {
            throw new ClientInputError("coupons must contain only non-empty GS1 strings.");
        }
    }

    return { redeem, rollback };
}

module.exports = { createCouponService };
