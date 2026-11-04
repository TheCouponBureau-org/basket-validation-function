"use strict";

function normalizeMode(mode) {
    const normalized = typeof mode === "string" ? mode.trim().toLowerCase() : "";

    if (!normalized || normalized === "retailer") {
        return "retailer";
    }

    if (normalized === "accelerator") {
        return "accelerator";
    }

    throw new TypeError("TCB mode must be retailer or accelerator.");
}

function resolveRetailerEmailDomain(mode, retailerEmailDomain) {
    if (mode !== "accelerator") {
        return undefined;
    }

    if (typeof retailerEmailDomain !== "string" || !retailerEmailDomain.trim()) {
        throw new TypeError("Accelerator redemption requires retailer_email_domain.");
    }

    return retailerEmailDomain.trim();
}

module.exports = {
    normalizeMode,
    resolveRetailerEmailDomain,
};
