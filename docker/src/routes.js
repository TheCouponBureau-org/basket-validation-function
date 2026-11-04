"use strict";

const express = require("express");
const { asyncRoute } = require("./http/errors");
const { getTcbRequestContext } = require("./http/request-context");

function createRoutes({ basketService, couponService, tokenManager }) {
    const router = express.Router();

    router.post(
        "/get_access_token",
        asyncRoute(async (_request, response) => {
            const token = await tokenManager.getAccessToken();
            response.json({ token });
        })
    );

    router.post(
        "/coupons_valid_for_basket",
        asyncRoute(async (request, response) => {
            const startTime = performance.now();
            const basketValidationOutput = await basketService.validateBasket(
                request.body,
                getTcbRequestContext(request)
            );
            response.json({
                basket_validation_output: basketValidationOutput,
                execution_time_in_ms: Math.round(performance.now() - startTime),
            });
        })
    );

    router.post(
        "/redeem_coupons",
        asyncRoute(async (request, response) => {
            const startTime = performance.now();
            const redeemedCoupons = await couponService.redeem(
                request.body?.coupons,
                getTcbRequestContext(request)
            );
            response.json({
                redeemed_coupons: redeemedCoupons,
                execution_time_in_ms: Math.round(performance.now() - startTime),
            });
        })
    );

    router.post(
        "/rollback_coupons",
        asyncRoute(async (request, response) => {
            const startTime = performance.now();
            const rolledBackCoupons = await couponService.rollback(
                request.body?.coupons,
                getTcbRequestContext(request)
            );
            response.json({
                rolled_back_coupons: rolledBackCoupons,
                execution_time_in_ms: Math.round(performance.now() - startTime),
            });
        })
    );

    return router;
}

module.exports = { createRoutes };
