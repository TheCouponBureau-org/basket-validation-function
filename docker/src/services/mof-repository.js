"use strict";

const MOF_KEY_PREFIX = "tcb:mof:";
const MOF_SYNC_MARKER_KEY = "tcb:mof-sync:last-success";

function createMofRepository(redis) {
    async function getPurchaseRequirement(baseGs1) {
        const value = await redis.get(`${MOF_KEY_PREFIX}${baseGs1}`);
        return value ? JSON.parse(value) : null;
    }

    async function saveRecords(records) {
        if (!Array.isArray(records) || records.length === 0) return;
        const transaction = redis.multi();
        for (const record of records) {
            transaction.set(
                `${MOF_KEY_PREFIX}${record.base_gs1}`,
                JSON.stringify(record.purchase_requirement)
            );
        }
        await transaction.exec();
    }

    async function getLastSuccessfulSync() {
        const value = await redis.get(MOF_SYNC_MARKER_KEY);
        return value ? JSON.parse(value) : null;
    }

    async function saveSuccessfulSync(summary) {
        await redis.set(MOF_SYNC_MARKER_KEY, JSON.stringify(summary));
    }

    return {
        getLastSuccessfulSync,
        getPurchaseRequirement,
        saveRecords,
        saveSuccessfulSync,
    };
}

module.exports = {
    MOF_KEY_PREFIX,
    MOF_SYNC_MARKER_KEY,
    createMofRepository,
};
