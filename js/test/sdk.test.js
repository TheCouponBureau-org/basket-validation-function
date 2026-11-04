"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const sdk = require("..");
const { resolveDateRange } = require("../services/mof-sync-service");

const originalFetch = global.fetch;

test.afterEach(() => {
    global.fetch = originalFetch;
});

test("local validation applies coupons in caller order", () => {
    const output = sdk.localBasketValidation({
        basket: [basketItem(2)],
        coupons: [localCoupon("coupon-first", 100), localCoupon("coupon-second", 50)],
    });

    assert.deepEqual(
        output.applied_coupons.map((coupon) => coupon.coupon_code),
        ["coupon-first", "coupon-second"]
    );
    assert.equal(output.discount_in_cents, 150);
});

test("serialized GS1s parse locally while a fetch code is resolved through TCB", async () => {
    const serialized = "8112009988459000019133924009755364";
    const fetchCode = "8112001234567890";
    let apiCalls = 0;

    global.fetch = async (_url, options) => {
        apiCalls += 1;
        assert.deepEqual(JSON.parse(options.body).gs1s, [fetchCode]);
        return jsonResponse({
            status: "success",
            newly_redeemed: [
                {
                    gs1: "8112109988459000269133321426026193",
                    master_offer_file: "811210998845900026",
                },
                {
                    gs1: "8112109988459000269133587761214614",
                    master_offer_file: "811210998845900026",
                },
            ],
        });
    };

    const resolved = await sdk.parseScannedGs1s(
        "https://api.example.test",
        "key",
        "token",
        [serialized, fetchCode]
    );

    assert.equal(apiCalls, 1);
    assert.deepEqual(resolved, [
        {
            gs1: serialized,
            base_gs1: "811200998845900001",
            validated: false,
        },
        {
            gs1: "8112109988459000269133321426026193",
            base_gs1: "811210998845900026",
            validated: true,
        },
        {
            gs1: "8112109988459000269133587761214614",
            base_gs1: "811210998845900026",
            validated: true,
        },
    ]);
});

test("TCB validation preserves input coupon order when the response order differs", async () => {
    const first = "8112009988459000019133924009755364";
    const second = "8112209988459000350001";
    const third = "8112009988459000039133742458606199";
    const resolvedSecond = "8112209988459000359165266614604064";

    global.fetch = async () =>
        jsonResponse({
            status: "success",
            newly_redeemed: [
                redeemed(third, "811200998845900003"),
                redeemed(resolvedSecond, "811220998845900035"),
                redeemed(first, "811200998845900001"),
            ],
            master_offer_files: {
                "811200998845900001": purchaseRequirement(30),
                "811220998845900035": purchaseRequirement(20),
                "811200998845900003": purchaseRequirement(10),
            },
        });

    const output = await sdk.validateBasketHelper({
        basket: [basketItem(3)],
        coupons: [first, second, third],
        tcb_base_url: "https://api.example.test",
        tcb_access_key: "key",
        tcb_access_token: "token",
    });

    assert.deepEqual(
        output.applied_coupons.map((coupon) => coupon.coupon_code),
        [first, resolvedSecond, third]
    );
});

test("final redemption chunks in parallel and reuses client transaction ID on retry", async () => {
    const requests = [];
    let failedLargeChunk = false;
    global.fetch = async (_url, options) => {
        const payload = JSON.parse(options.body);
        requests.push(payload);

        if (payload.gs1s.length === 15 && !failedLargeChunk) {
            failedLargeChunk = true;
            return jsonResponse({ status: "error" }, 500);
        }
        return jsonResponse({
            status: "success",
            status_code: "FULL_REDEMPTION",
            newly_redeemed: payload.gs1s.map((gs1) => redeemed(gs1, gs1.slice(0, 18))),
            total_gs1s_processed: payload.gs1s.length,
            master_offer_files: {},
        });
    };

    const gs1s = Array.from({ length: 16 }, (_, index) => `coupon-${index}`);
    const response = await sdk.redeemCoupons(
        "https://api.example.test",
        "key",
        "token",
        gs1s
    );

    const largeRequests = requests.filter((request) => request.gs1s.length === 15);
    assert.equal(requests.length, 3);
    assert.equal(largeRequests.length, 2);
    assert.equal(largeRequests[0].client_txn_id, largeRequests[1].client_txn_id);
    assert.equal(response.total_gs1s_processed, 16);
});

test("MOF sync maps nextPageNo and SDK purchase requirement shape", async () => {
    let requestedUrl;
    global.fetch = async (url) => {
        requestedUrl = url;
        return jsonResponse({
            status: "success",
            data: [
                {
                    base_gs1: "811200998845900001",
                    primary_purchase_gtins: ["037000930396"],
                    primary_purchase_requirements: 1,
                    funder_id: "ignored",
                },
            ],
            nextPageNo: "page-2",
        });
    };

    const response = await sdk.syncMasterOfferFiles(
        "https://api.example.test",
        "key",
        "token",
        "incremental"
    );

    assert.match(requestedUrl, /\/syncmof\/\d{4}-\d{2}-\d{2}\/\d{4}-\d{2}-\d{2}\/updated\?pageNo=$/);
    assert.equal(response.next_page_no, "page-2");
    assert.deepEqual(response.data[0], {
        base_gs1: "811200998845900001",
        purchase_requirement: {
            primary_purchase_gtins: ["037000930396"],
            primary_purchase_requirements: 1,
        },
    });
});

test("MOF date ranges end tomorrow", () => {
    const now = new Date("2026-08-18T12:00:00Z");
    assert.deepEqual(resolveDateRange("initial", now), {
        fromDate: "2026-02-18",
        toDate: "2026-08-19",
    });
    assert.deepEqual(resolveDateRange("incremental", now), {
        fromDate: "2026-08-17",
        toDate: "2026-08-19",
    });

    assert.deepEqual(resolveDateRange("initial", new Date("2026-08-31T12:00:00Z")), {
        fromDate: "2026-02-28",
        toDate: "2026-09-01",
    });
});

function basketItem(quantity) {
    return {
        product_code: "037000930396",
        price: 1.29,
        quantity,
        unit: "item",
    };
}

function localCoupon(gs1, saveValue) {
    return { gs1, purchase_requirement: purchaseRequirement(saveValue) };
}

function purchaseRequirement(saveValue) {
    return {
        primary_purchase_gtins: ["037000930396"],
        primary_purchase_save_value: saveValue,
        primary_purchase_requirements: 1,
        primary_purchase_req_code: 0,
        save_value_code: 0,
    };
}

function redeemed(gs1, baseGs1) {
    return { gs1, master_offer_file: baseGs1 };
}

function jsonResponse(body, status = 200) {
    return new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
    });
}
