"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createMofSyncService } = require("../src/services/mof-sync-service");

test("initial sync stores every MOF page and records completion", async () => {
    const savedPages = [];
    let savedSummary;
    const lockValues = new Map();
    const redis = {
        set: async (key, value, options) => {
            if (options?.NX && lockValues.has(key)) return null;
            lockValues.set(key, value);
            return "OK";
        },
        eval: async (_script, { keys, arguments: values }) => {
            if (lockValues.get(keys[0]) === values[0]) lockValues.delete(keys[0]);
        },
    };
    const sdk = {
        syncMasterOfferFiles: async (_endpoint, _key, _token, mode, pageNo) => {
            assert.equal(mode, "initial");
            return pageNo === ""
                ? { data: [{ base_gs1: "base-one" }], next_page_no: "page-2" }
                : { data: [{ base_gs1: "base-two" }], next_page_no: "-1" };
        },
    };
    const repository = {
        getLastSuccessfulSync: async () => null,
        saveRecords: async (records) => savedPages.push(records),
        saveSuccessfulSync: async (summary) => {
            savedSummary = summary;
        },
    };
    const service = createMofSyncService({
        sdk,
        redis,
        repository,
        tokenManager: { getAccessToken: async () => "token" },
        config: {
            tcbEndpoint: "https://api.example.test",
            tcbAccessKey: "key",
            mofSyncCron: "0 2 * * *",
        },
    });

    await service.syncOnStart();

    assert.deepEqual(savedPages, [
        [{ base_gs1: "base-one" }],
        [{ base_gs1: "base-two" }],
    ]);
    assert.equal(savedSummary.mode, "initial");
    assert.equal(savedSummary.page_count, 2);
    assert.equal(savedSummary.record_count, 2);
});
