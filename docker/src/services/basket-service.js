"use strict";

const { ClientInputError } = require("../http/errors");

function createBasketService({ sdk, repository, tokenManager, config }) {
    async function validateBasket(input, context) {
        if (!Array.isArray(input?.basket) || !Array.isArray(input?.coupons)) {
            throw new ClientInputError("basket and coupons are required arrays.");
        }

        const scannedGs1s = extractGs1s(input.coupons);
        if (scannedGs1s.length === 0) {
            return { discount_in_cents: 0, applied_coupons: [] };
        }

        const accessToken = await tokenManager.getAccessToken();
        const resolved = await sdk.parseScannedGs1s(
            config.tcbEndpoint,
            config.tcbAccessKey,
            accessToken,
            scannedGs1s,
            context.mode,
            context.retailerEmailDomain
        );
        const coupons = await loadPurchaseRequirements(resolved);

        return sdk.validateBasketHelper({
            basket: input.basket,
            coupons,
            tcb_base_url: config.tcbEndpoint,
            tcb_access_key: config.tcbAccessKey,
            tcb_access_token: accessToken,
            mode: context.mode,
            retailer_email_domain: context.retailerEmailDomain,
            enable_logging: input.enable_logging === true,
        });
    }

    async function loadPurchaseRequirements(resolvedCoupons) {
        return Promise.all(
            resolvedCoupons.map(async (coupon) => {
                const purchaseRequirement = await repository.getPurchaseRequirement(
                    coupon.base_gs1
                );

                if (!purchaseRequirement) {
                    return { gs1: coupon.gs1, validated: false };
                }

                return {
                    gs1: coupon.gs1,
                    purchase_requirement: purchaseRequirement,
                    validated: coupon.validated,
                };
            })
        );
    }

    function extractGs1s(coupons) {
        return coupons.map((coupon, index) => {
            const gs1 = typeof coupon === "string" ? coupon : coupon?.gs1;
            if (typeof gs1 !== "string" || !gs1.trim()) {
                throw new ClientInputError("Every coupon must contain a gs1 value.", {
                    coupon_index: index,
                });
            }
            return gs1.trim();
        });
    }

    return { validateBasket };
}

module.exports = { createBasketService };
