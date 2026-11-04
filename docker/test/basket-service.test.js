"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createBasketService } = require("../src/services/basket-service");

test("loads local purchase requirements and falls back for cache misses", async () => {
    let validationInput;
    const sdk = {
        parseScannedGs1s: async () => [
            { gs1: "coupon-one", base_gs1: "base-one", validated: false },
            { gs1: "coupon-two", base_gs1: "base-two", validated: true },
        ],
        validateBasketHelper: async (input) => {
            validationInput = input;
            return { discount_in_cents: 100, applied_coupons: [] };
        },
    };
    const repository = {
        getPurchaseRequirement: async (baseGs1) =>
            baseGs1 === "base-one" ? { primary_purchase_requirements: 1 } : null,
    };
    const service = createBasketService({
        sdk,
        repository,
        tokenManager: { getAccessToken: async () => "token" },
        config: {
            tcbEndpoint: "https://api.example.test",
            tcbAccessKey: "key",
        },
    });

    await service.validateBasket(
        { basket: [], coupons: ["coupon-one", "coupon-two"] },
        { mode: "retailer" }
    );

    assert.deepEqual(validationInput.coupons, [
        {
            gs1: "coupon-one",
            purchase_requirement: { primary_purchase_requirements: 1 },
            validated: false,
        },
        { gs1: "coupon-two", validated: false },
    ]);
});
