"use strict";

function getTcbRequestContext(request) {
    const retailerEmailDomain = firstValue(
        request.body?.retailer_email_domain,
        request.headers.retailer_email_domain,
        request.headers["retailer-email-domain"],
        request.headers["x-retailer-email-domain"],
        request.headers["x-retailer-domain"]
    );
    const explicitMode = firstValue(
        request.body?.mode,
        request.headers["x-tcb-mode"],
        request.headers.mode
    );

    return {
        mode: explicitMode || (retailerEmailDomain ? "accelerator" : "retailer"),
        retailerEmailDomain,
    };
}

function firstValue(...values) {
    return values.find((value) => typeof value === "string" && value.trim())?.trim();
}

module.exports = { getTcbRequestContext };
