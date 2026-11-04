"use strict";

const { STANDARD_RETRY_BACKOFF_MS } = require("./constants");

function delay(milliseconds) {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function requestWithRetry(url, options, operationName, retryOptions = {}) {
    const backoffMs = retryOptions.backoffMs || STANDARD_RETRY_BACKOFF_MS;
    const shouldRetryStatus = retryOptions.shouldRetryStatus || (() => true);
    let lastError;
    let lastResponse;

    for (let attempt = 0; attempt <= backoffMs.length; attempt += 1) {
        try {
            const response = await fetch(url, options);
            if (response.ok) {
                return response;
            }

            lastResponse = response;
            if (!shouldRetryStatus(response.status)) {
                break;
            }
        } catch (error) {
            lastError = error;
        }

        if (attempt < backoffMs.length) {
            await delay(backoffMs[attempt]);
        }
    }

    if (lastResponse) {
        const responseBody = await lastResponse.text().catch(() => "");
        const suffix = responseBody ? `: ${responseBody}` : "";
        throw new Error(
            `TCB API call failed for ${operationName} after retries with HTTP ${lastResponse.status}${suffix}`
        );
    }

    throw new Error(`TCB API call failed for ${operationName} after retries.`, { cause: lastError });
}

function jsonHeaders(accessKey, accessToken) {
    const headers = {
        "Content-Type": "application/json",
        "x-api-key": accessKey,
    };

    if (accessToken) {
        headers["x-access-token"] = accessToken;
    }

    return headers;
}

async function postJson(url, accessKey, accessToken, body, operationName) {
    return requestWithRetry(
        url,
        {
            method: "POST",
            headers: jsonHeaders(accessKey, accessToken),
            body: JSON.stringify(body),
        },
        operationName
    );
}

async function deleteRequest(url, accessKey, accessToken, operationName) {
    return requestWithRetry(
        url,
        {
            method: "DELETE",
            headers: jsonHeaders(accessKey, accessToken),
        },
        operationName
    );
}

module.exports = {
    deleteRequest,
    jsonHeaders,
    postJson,
    requestWithRetry,
};
