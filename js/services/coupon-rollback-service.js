"use strict";

const { deleteRequest } = require("../lib/tcb-api-client");
const { normalizeMode } = require("../lib/tcb-mode");
const { isBlank, normalizeBaseUrl, requireTcbCredentials } = require("../lib/validation");

async function rollbackCoupons(baseUrl, accessKey, accessToken, gs1s, mode) {
    requireTcbCredentials(baseUrl, accessKey, accessToken, "TCB rollback");
    if (!Array.isArray(gs1s) || gs1s.length === 0) {
        throw new TypeError("At least one gs1 is required for coupon rollback.");
    }

    const requestMode = normalizeMode(mode);
    const entries = await Promise.all(
        gs1s.map(async (gs1) => {
            if (isBlank(gs1)) {
                throw new TypeError("GS1 cannot be blank for rollback.");
            }

            const response = await deleteRequest(
                `${normalizeBaseUrl(baseUrl)}/${requestMode}/rollback/${encodeURIComponent(gs1)}`,
                accessKey,
                accessToken,
                `${requestMode}/rollback/${gs1}`
            );
            return [gs1, await response.text()];
        })
    );

    return Object.fromEntries(entries);
}

module.exports = { rollbackCoupons };
