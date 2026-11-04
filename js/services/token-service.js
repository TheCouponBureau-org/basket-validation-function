"use strict";

const { postJson } = require("../lib/tcb-api-client");
const { isBlank, normalizeBaseUrl } = require("../lib/validation");

async function fetchAccessTokenResponse(baseUrl, accessKey, secretKey) {
    if (isBlank(baseUrl) || isBlank(accessKey) || isBlank(secretKey)) {
        throw new TypeError("TCB token fetch requires baseUrl, accessKey, and secretKey.");
    }

    const response = await postJson(
        `${normalizeBaseUrl(baseUrl)}/access_token`,
        accessKey,
        undefined,
        { access_key: accessKey, secret_key: secretKey },
        "access_token"
    );
    const tokenResponse = await response.json();

    if (isBlank(tokenResponse?.["x-access-token"])) {
        throw new Error("TCB access_token response did not contain x-access-token.");
    }

    return tokenResponse;
}

async function fetchAccessToken(baseUrl, accessKey, secretKey) {
    const response = await fetchAccessTokenResponse(baseUrl, accessKey, secretKey);
    return response["x-access-token"];
}

module.exports = {
    fetchAccessToken,
    fetchAccessTokenResponse,
};
