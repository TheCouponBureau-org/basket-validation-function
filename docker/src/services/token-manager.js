"use strict";

const TOKEN_CACHE_KEY = "tcb:basket-validator:access-token";

function createTokenManager({ sdk, redis, config }) {
    let refreshPromise;

    async function getAccessToken() {
        const cachedToken = await redis.get(TOKEN_CACHE_KEY);
        if (cachedToken) return cachedToken;

        if (!refreshPromise) {
            refreshPromise = refreshAccessToken().finally(() => {
                refreshPromise = undefined;
            });
        }
        return refreshPromise;
    }

    async function refreshAccessToken() {
        const token = await sdk.fetchAccessToken(
            config.tcbEndpoint,
            config.tcbAccessKey,
            config.tcbSecretKey
        );
        await redis.set(TOKEN_CACHE_KEY, token, { EX: config.tokenTtlSeconds });
        return token;
    }

    return { getAccessToken, refreshAccessToken };
}

module.exports = { TOKEN_CACHE_KEY, createTokenManager };
