"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createTokenManager, TOKEN_CACHE_KEY } = require("../src/services/token-manager");

test("fetches a token once and reuses it from Redis", async () => {
    const values = new Map();
    const redis = {
        get: async (key) => values.get(key) || null,
        set: async (key, value, options) => {
            assert.deepEqual(options, { EX: 82_800 });
            values.set(key, value);
        },
    };
    let fetchCount = 0;
    const sdk = {
        fetchAccessToken: async () => {
            fetchCount += 1;
            return "access-token";
        },
    };
    const manager = createTokenManager({
        sdk,
        redis,
        config: {
            tcbEndpoint: "https://api.example.test",
            tcbAccessKey: "key",
            tcbSecretKey: "secret",
            tokenTtlSeconds: 82_800,
        },
    });

    assert.equal(await manager.getAccessToken(), "access-token");
    assert.equal(await manager.getAccessToken(), "access-token");
    assert.equal(values.get(TOKEN_CACHE_KEY), "access-token");
    assert.equal(fetchCount, 1);
});
