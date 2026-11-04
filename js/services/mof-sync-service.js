"use strict";

const { MOF_RETRY_BACKOFF_MS } = require("../lib/constants");
const { jsonHeaders, requestWithRetry } = require("../lib/tcb-api-client");
const { isBlank, normalizeBaseUrl, requireTcbCredentials } = require("../lib/validation");

const PURCHASE_REQUIREMENT_FIELDS = [
    "primary_purchase_gtins",
    "primary_purchase_eans",
    "excluded_primary_purchase_gtins",
    "excluded_primary_purchase_eans",
    "primary_purchase_prefixed_code",
    "excluded_primary_purchase_prefixed_code",
    "primary_purchase_save_value",
    "primary_purchase_requirements",
    "primary_purchase_req_code",
    "second_purchase_gtins",
    "second_purchase_eans",
    "excluded_second_purchase_gtins",
    "excluded_second_purchase_eans",
    "second_purchase_prefixed_code",
    "excluded_second_purchase_prefixed_code",
    "second_purchase_save_value",
    "second_purchase_requirements",
    "second_purchase_req_code",
    "third_purchase_gtins",
    "third_purchase_eans",
    "excluded_third_purchase_gtins",
    "excluded_third_purchase_eans",
    "third_purchase_prefixed_code",
    "excluded_third_purchase_prefixed_code",
    "third_purchase_save_value",
    "third_purchase_requirements",
    "third_purchase_req_code",
    "save_value_code",
    "applies_to_which_item",
    "additional_purchase_rules_code",
];

/** Pulls one page of MOF records and maps each record to SDK input shape. */
async function syncMasterOfferFiles(baseUrl, accessKey, accessToken, mode, pageNo = "") {
    requireTcbCredentials(baseUrl, accessKey, accessToken, "MOF sync");
    const dateRange = resolveDateRange(mode);
    const url = buildSyncUrl(baseUrl, dateRange, pageNo);
    const response = await requestWithRetry(
        url,
        { method: "GET", headers: jsonHeaders(accessKey, accessToken) },
        "syncmof",
        {
            backoffMs: MOF_RETRY_BACKOFF_MS,
            shouldRetryStatus: (status) => status >= 500,
        }
    );
    return mapSyncResponse(await response.json());
}

function resolveDateRange(mode, now = new Date()) {
    const normalizedMode = typeof mode === "string" ? mode.trim().toLowerCase() : "";
    const today = utcDateOnly(now);
    const toDate = addUtcDays(today, 1);
    let fromDate;

    if (normalizedMode === "initial") {
        fromDate = addUtcMonths(today, -6);
    } else if (normalizedMode === "incremental") {
        fromDate = addUtcDays(today, -1);
    } else {
        throw new TypeError("mode must be either initial or incremental.");
    }

    return { fromDate: formatDate(fromDate), toDate: formatDate(toDate) };
}

function buildSyncUrl(baseUrl, dateRange, pageNo) {
    const page = pageNo == null ? "" : String(pageNo);
    return `${normalizeBaseUrl(baseUrl)}/syncmof/${dateRange.fromDate}/${dateRange.toDate}/updated?pageNo=${encodeURIComponent(page)}`;
}

function mapSyncResponse(response) {
    return {
        status: response?.status,
        data: Array.isArray(response?.data) ? response.data.map(mapRecord).filter(Boolean) : [],
        next_page_no: response?.nextPageNo ?? response?.next_page_no ?? null,
        execution_id: response?.execution_id,
        execution_time_in_ms: response?.execution_time_in_ms,
        execution_start_time: response?.execution_start_time,
    };
}

function mapRecord(record) {
    const baseGs1 = record?.base_gs1 ?? record?.baseGs1;
    if (isBlank(baseGs1)) return null;

    const purchaseRequirement = {};
    for (const field of PURCHASE_REQUIREMENT_FIELDS) {
        if (record[field] !== undefined && record[field] !== null) {
            purchaseRequirement[field] = record[field];
        }
    }
    return { base_gs1: baseGs1, purchase_requirement: purchaseRequirement };
}

function utcDateOnly(value) {
    return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth(), value.getUTCDate()));
}

function addUtcDays(value, days) {
    const result = new Date(value);
    result.setUTCDate(result.getUTCDate() + days);
    return result;
}

function addUtcMonths(value, months) {
    const result = new Date(value);
    const originalDay = result.getUTCDate();
    result.setUTCDate(1);
    result.setUTCMonth(result.getUTCMonth() + months);
    const lastDayOfTargetMonth = new Date(
        Date.UTC(result.getUTCFullYear(), result.getUTCMonth() + 1, 0)
    ).getUTCDate();
    result.setUTCDate(Math.min(originalDay, lastDayOfTargetMonth));
    return result;
}

function formatDate(value) {
    return value.toISOString().slice(0, 10);
}

module.exports = {
    buildSyncUrl,
    mapRecord,
    mapSyncResponse,
    resolveDateRange,
    syncMasterOfferFiles,
};
