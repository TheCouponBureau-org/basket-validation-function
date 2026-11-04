"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createApp } = require("../src/app");

test("exposes the reference validation response contract", async (context) => {
    let receivedContext;
    const app = createApp({
        basketService: {
            validateBasket: async (_input, tcbContext) => {
                receivedContext = tcbContext;
                return { discount_in_cents: 100, applied_coupons: [] };
            },
        },
        couponService: { redeem: async () => ({}), rollback: async () => ({}) },
        tokenManager: { getAccessToken: async () => "token" },
    });
    const server = app.listen(0);
    context.after(() => server.close());
    await new Promise((resolve) => server.once("listening", resolve));
    const { port } = server.address();

    const response = await fetch(`http://127.0.0.1:${port}/coupons_valid_for_basket`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            basket: [],
            coupons: ["8112009988459000019133924009755364"],
            retailer_email_domain: "retailer.example",
        }),
    });
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.deepEqual(body.basket_validation_output, {
        discount_in_cents: 100,
        applied_coupons: [],
    });
    assert.equal(receivedContext.mode, "accelerator");
    assert.equal(receivedContext.retailerEmailDomain, "retailer.example");
});

test("returns a clean 400 response for malformed JSON", async (context) => {
    const app = createApp({
        basketService: {},
        couponService: {},
        tokenManager: {},
    });
    const server = app.listen(0);
    context.after(() => server.close());
    await new Promise((resolve) => server.once("listening", resolve));
    const { port } = server.address();

    const response = await fetch(`http://127.0.0.1:${port}/coupons_valid_for_basket`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{invalid",
    });

    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), {
        status: "error",
        message: "Invalid JSON payload",
    });
});
