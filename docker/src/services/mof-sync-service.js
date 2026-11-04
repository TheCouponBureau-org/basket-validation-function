"use strict";

const cron = require("node-cron");
const { randomUUID } = require("node:crypto");

const MOF_SYNC_LOCK_KEY = "tcb:mof-sync:lock";
const LOCK_TTL_SECONDS = 3_600;

function createMofSyncService({ sdk, redis, repository, tokenManager, config }) {
    async function sync(mode) {
        const lockOwner = randomUUID();
        const lockAcquired = await redis.set(MOF_SYNC_LOCK_KEY, lockOwner, {
            NX: true,
            EX: LOCK_TTL_SECONDS,
        });
        if (!lockAcquired) {
            console.log("[MOF Sync] Skipped because another sync is running.");
            return { skipped: true };
        }

        try {
            const accessToken = await tokenManager.getAccessToken();
            let pageNo = "";
            let recordCount = 0;
            let pageCount = 0;

            do {
                const page = await sdk.syncMasterOfferFiles(
                    config.tcbEndpoint,
                    config.tcbAccessKey,
                    accessToken,
                    mode,
                    pageNo
                );
                await repository.saveRecords(page.data);
                recordCount += page.data.length;
                pageCount += 1;
                pageNo = page.next_page_no;
            } while (pageNo && pageNo !== "-1");

            const summary = {
                mode,
                page_count: pageCount,
                record_count: recordCount,
                completed_at: new Date().toISOString(),
            };
            await repository.saveSuccessfulSync(summary);
            console.log("[MOF Sync] Completed", summary);
            return summary;
        } finally {
            await releaseLock(redis, lockOwner);
        }
    }

    async function syncOnStart() {
        const previousSync = await repository.getLastSuccessfulSync();
        return sync(previousSync ? "incremental" : "initial");
    }

    function schedule() {
        if (!cron.validate(config.mofSyncCron)) {
            throw new Error(`Invalid MOF_SYNC_CRON value: ${config.mofSyncCron}`);
        }

        return cron.schedule(config.mofSyncCron, () => {
            sync("incremental").catch((error) => {
                console.error("[MOF Sync] Scheduled sync failed:", error.message);
            });
        });
    }

    return { schedule, sync, syncOnStart };
}

async function releaseLock(redis, lockOwner) {
    await redis.eval(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        { keys: [MOF_SYNC_LOCK_KEY], arguments: [lockOwner] }
    );
}

module.exports = { createMofSyncService };
