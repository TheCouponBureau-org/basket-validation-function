"use strict";

const sdk = require("@thecouponbureau/basket-validator");
const { createApp } = require("./app");
const { loadConfig } = require("./config");
const { createRedisClient } = require("./infra/redis-client");
const { createBasketService } = require("./services/basket-service");
const { createCouponService } = require("./services/coupon-service");
const { createMofRepository } = require("./services/mof-repository");
const { createMofSyncService } = require("./services/mof-sync-service");
const { createTokenManager } = require("./services/token-manager");

async function start() {
    const config = loadConfig();
    const redis = await createRedisClient(config.redisUrl);
    const repository = createMofRepository(redis);
    const tokenManager = createTokenManager({ sdk, redis, config });
    const basketService = createBasketService({ sdk, repository, tokenManager, config });
    const couponService = createCouponService({ sdk, tokenManager, config });
    const mofSyncService = createMofSyncService({
        sdk,
        redis,
        repository,
        tokenManager,
        config,
    });

    const app = createApp({
        basketService,
        couponService,
        tokenManager,
        asyncHealthCheck: async (_request, response, next) => {
            try {
                await redis.ping();
                response.json({ status: "ok", redis: "connected" });
            } catch (error) {
                next(error);
            }
        },
    });
    const server = app.listen(config.port, () => {
        console.log(`[Server] Listening on port ${config.port}`);
    });

    const scheduledSync = mofSyncService.schedule();
    if (config.mofSyncOnStart) {
        mofSyncService.syncOnStart().catch((error) => {
            console.error("[MOF Sync] Startup sync failed:", error.message);
        });
    }

    async function shutdown(signal) {
        console.log(`[Server] Received ${signal}; shutting down.`);
        scheduledSync.stop();
        server.close();
        await redis.quit();
    }

    process.once("SIGTERM", () => void shutdown("SIGTERM"));
    process.once("SIGINT", () => void shutdown("SIGINT"));
}

start().catch((error) => {
    console.error("[Startup Error]", error.message);
    process.exitCode = 1;
});
