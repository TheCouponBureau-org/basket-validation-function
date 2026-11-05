"use strict";

const { timingSafeEqual } = require("node:crypto");

function createApiKeyMiddleware(expectedApiKey) {
    return (request, response, next) => {
        if (typeof expectedApiKey !== "string" || !expectedApiKey) {
            return next();
        }

        const providedApiKey = request.get("x-api-key");
        if (!apiKeysMatch(providedApiKey, expectedApiKey)) {
            return response.status(401).json({
                status: "error",
                message: "Invalid or missing API key.",
            });
        }
        return next();
    };
}

function apiKeysMatch(providedApiKey, expectedApiKey) {
    if (typeof providedApiKey !== "string") return false;
    const provided = Buffer.from(providedApiKey);
    const expected = Buffer.from(expectedApiKey);
    return provided.length === expected.length && timingSafeEqual(provided, expected);
}

module.exports = { apiKeysMatch, createApiKeyMiddleware };
