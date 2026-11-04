"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { loadConfig } = require("../src/config");

test("loads required TCB credentials and operational defaults", () => {
    const config = loadConfig({
        TCB_ENDPOINT: "https://api.example.test",
        TCB_ACCESS_KEY: "key",
        TCB_SECRET_KEY: "secret",
    });

    assert.equal(config.port, 3000);
    assert.equal(config.redisUrl, "redis://redis:6379");
    assert.equal(config.mofSyncCron, "0 */4 * * *");
    assert.equal(config.tokenTtlSeconds, 82_800);
});

test("rejects missing TCB environment configuration", () => {
    assert.throws(() => loadConfig({}), /TCB_ENDPOINT, TCB_ACCESS_KEY, TCB_SECRET_KEY/);
});
