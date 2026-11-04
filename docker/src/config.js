"use strict";

function loadConfig(environment = process.env) {
    const config = {
        port: parsePositiveInteger(environment.PORT, 3000),
        tcbEndpoint: environment.TCB_ENDPOINT || environment.TBC_ENDPOINT,
        tcbAccessKey: environment.TCB_ACCESS_KEY,
        tcbSecretKey: environment.TCB_SECRET_KEY,
        redisUrl: environment.REDIS_URL || "redis://redis:6379",
        mofSyncCron: environment.MOF_SYNC_CRON || "0 */4 * * *",
        mofSyncOnStart: parseBoolean(environment.MOF_SYNC_ON_START, true),
        tokenTtlSeconds: parsePositiveInteger(environment.TCB_TOKEN_TTL_SECONDS, 82_800),
    };

    const missing = [
        ["TCB_ENDPOINT", config.tcbEndpoint],
        ["TCB_ACCESS_KEY", config.tcbAccessKey],
        ["TCB_SECRET_KEY", config.tcbSecretKey],
    ]
        .filter(([, value]) => !value)
        .map(([name]) => name);

    if (missing.length > 0) {
        throw new Error(`Missing required environment variables: ${missing.join(", ")}`);
    }

    return Object.freeze(config);
}

function parsePositiveInteger(value, defaultValue) {
    if (value === undefined || value === "") return defaultValue;
    const parsed = Number.parseInt(value, 10);
    if (!Number.isInteger(parsed) || parsed <= 0) {
        throw new Error(`Expected a positive integer but received: ${value}`);
    }
    return parsed;
}

function parseBoolean(value, defaultValue) {
    if (value === undefined || value === "") return defaultValue;
    return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

module.exports = { loadConfig };
