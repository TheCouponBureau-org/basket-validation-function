"use strict";

const { REDEEM_CHUNK_SIZE, SINGLE_FETCH_CODE_LENGTH } = require("../lib/constants");
const { chunk, findMatchingRedeemedCoupon, tryParseConsumerSerializedGs1s } = require("../lib/coupon-utils");
const { postJson } = require("../lib/tcb-api-client");
const { normalizeMode, resolveRetailerEmailDomain } = require("../lib/tcb-mode");
const { isBlank, normalizeBaseUrl, requireTcbCredentials } = require("../lib/validation");

async function parseScannedGs1s(
    baseUrl,
    accessKey,
    accessToken,
    scannedGs1s,
    mode,
    retailerEmailDomain
) {
    requireTcbCredentials(baseUrl, accessKey, accessToken, "TCB scanned gs1 parsing");
    if (!Array.isArray(scannedGs1s) || scannedGs1s.length === 0) {
        throw new TypeError("At least one scanned gs1 is required.");
    }

    const requestMode = normalizeMode(mode);
    const emailDomain = resolveRetailerEmailDomain(requestMode, retailerEmailDomain);
    const resolvedByInputIndex = scannedGs1s.map(() => []);
    const pending = [];

    scannedGs1s.forEach((value, inputIndex) => {
        if (isBlank(value)) return;
        const gs1 = value.trim();
        const locallyParsed = tryParseConsumerSerializedGs1s(gs1);
        if (locallyParsed.length > 0) {
            resolvedByInputIndex[inputIndex].push(...locallyParsed);
        } else {
            pending.push({ inputIndex, gs1 });
        }
    });

    const fetchCodeChunks = pending
        .filter((item) => item.gs1.length === SINGLE_FETCH_CODE_LENGTH)
        .map((item) => [item]);
    const otherChunks = chunk(
        pending.filter((item) => item.gs1.length !== SINGLE_FETCH_CODE_LENGTH),
        REDEEM_CHUNK_SIZE
    );

    const responses = await Promise.all(
        [...fetchCodeChunks, ...otherChunks].map((inputs) =>
            resolveScannedChunk(baseUrl, accessKey, accessToken, inputs, requestMode, emailDomain)
        )
    );

    for (const resolvedMap of responses) {
        for (const [inputIndex, resolved] of resolvedMap) {
            resolvedByInputIndex[inputIndex].push(...resolved);
        }
    }

    return resolvedByInputIndex.flat();
}

async function resolveScannedChunk(
    baseUrl,
    accessKey,
    accessToken,
    inputs,
    mode,
    retailerEmailDomain
) {
    const payload = {
        gs1s: inputs.map((item) => item.gs1),
        pre_process: "yes",
        include_check_digit: "yes",
        no_purchase_requirement: "yes",
        offline: "",
    };
    if (retailerEmailDomain) payload.retailer_email_domain = retailerEmailDomain;

    const response = await postJson(
        `${normalizeBaseUrl(baseUrl)}/retailer/redeem`,
        accessKey,
        accessToken,
        payload,
        `${mode}/redeem`
    );
    return mapResolvedScans(await response.json(), inputs);
}

function mapResolvedScans(response, inputs) {
    const newlyRedeemed = Array.isArray(response?.newly_redeemed)
        ? response.newly_redeemed
        : [];
    const resolved = new Map();

    for (const input of inputs) {
        let matches;
        if (input.gs1.length === SINGLE_FETCH_CODE_LENGTH) {
            matches = newlyRedeemed;
        } else {
            const match = findMatchingRedeemedCoupon(input.gs1, newlyRedeemed);
            matches = match ? [match] : [];
        }

        const mapped = matches
            .filter((item) => item?.gs1 && item?.master_offer_file)
            .map((item) => ({
                gs1: item.gs1,
                base_gs1: item.master_offer_file,
                validated: true,
            }));
        if (mapped.length > 0) resolved.set(input.inputIndex, mapped);
    }
    return resolved;
}

module.exports = {
    mapResolvedScans,
    parseScannedGs1s,
    tryParseConsumerSerializedGs1s,
};
