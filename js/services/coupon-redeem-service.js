"use strict";

const { randomUUID } = require("node:crypto");
const { REDEEM_CHUNK_SIZE } = require("../lib/constants");
const { chunk } = require("../lib/coupon-utils");
const { postJson } = require("../lib/tcb-api-client");
const { normalizeMode, resolveRetailerEmailDomain } = require("../lib/tcb-mode");
const { normalizeBaseUrl, requireTcbCredentials } = require("../lib/validation");

async function redeemCoupons(
    baseUrl,
    accessKey,
    accessToken,
    gs1s,
    mode,
    retailerEmailDomain
) {
    requireTcbCredentials(baseUrl, accessKey, accessToken, "TCB redeem");
    if (!Array.isArray(gs1s) || gs1s.length === 0) {
        throw new TypeError("At least one gs1 is required for coupon redemption.");
    }

    const requestMode = normalizeMode(mode);
    const emailDomain = resolveRetailerEmailDomain(requestMode, retailerEmailDomain);
    const responses = await Promise.all(
        chunk(gs1s, REDEEM_CHUNK_SIZE).map((gs1Chunk) =>
            redeemChunk(baseUrl, accessKey, accessToken, gs1Chunk, requestMode, emailDomain)
        )
    );

    return mergeRedeemResponses(responses);
}

async function redeemChunk(baseUrl, accessKey, accessToken, gs1s, mode, retailerEmailDomain) {
    const payload = {
        gs1s: [...gs1s],
        include_check_digit: "yes",
        no_purchase_requirement: "",
        offline: "",
        client_txn_id: randomUUID(),
    };

    if (retailerEmailDomain) {
        payload.retailer_email_domain = retailerEmailDomain;
    }

    const response = await postJson(
        `${normalizeBaseUrl(baseUrl)}/retailer/redeem`,
        accessKey,
        accessToken,
        payload,
        `${mode}/redeem`
    );
    return response.json();
}

function mergeRedeemResponses(responses) {
    if (!Array.isArray(responses) || responses.length === 0) {
        throw new TypeError("At least one redeem response is required.");
    }

    const statusCodes = new Set();
    const messages = new Set();
    const executionIds = [];
    const merged = {
        status: "success",
        status_code: "",
        newly_redeemed: [],
        total_gs1s_processed: 0,
        message: "",
        master_offer_files: {},
        execution_time_in_ms: 0,
    };

    for (const response of responses) {
        if (response?.status && response.status !== "success") {
            merged.status = response.status;
        }
        if (response?.status_code) statusCodes.add(response.status_code);
        if (response?.message) messages.add(response.message);
        if (response?.execution_id) executionIds.push(response.execution_id);
        if (Array.isArray(response?.newly_redeemed)) {
            merged.newly_redeemed.push(...response.newly_redeemed);
        }
        Object.assign(merged.master_offer_files, response?.master_offer_files || {});
        merged.total_gs1s_processed += Number(response?.total_gs1s_processed || 0);
        merged.execution_time_in_ms += Number(response?.execution_time_in_ms || 0);

        if (merged.emailDomain === undefined && response?.emailDomain !== undefined) {
            merged.emailDomain = response.emailDomain;
        }
        if (merged.pre_process === undefined && response?.pre_process !== undefined) {
            merged.pre_process = response.pre_process;
        }
        if (response?.execution_start_time !== undefined) {
            merged.execution_start_time = Math.min(
                merged.execution_start_time ?? response.execution_start_time,
                response.execution_start_time
            );
        }
        if (response?.event_timestamp !== undefined) {
            merged.event_timestamp = Math.max(
                merged.event_timestamp ?? response.event_timestamp,
                response.event_timestamp
            );
        }
    }

    merged.status_code = statusCodes.size === 1 ? [...statusCodes][0] : [...statusCodes].join(",");
    merged.message =
        messages.size === 1
            ? [...messages][0]
            : `Redeemed ${merged.newly_redeemed.length} gs1(s)`;

    if (executionIds.length === 1) {
        merged.execution_id = executionIds[0];
    } else if (executionIds.length > 1) {
        merged.execution_ids = executionIds;
    }

    return merged;
}

module.exports = {
    mergeRedeemResponses,
    redeemCoupons,
};
