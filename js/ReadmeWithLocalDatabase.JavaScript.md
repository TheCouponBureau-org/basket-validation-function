# JavaScript Integration with an Optional Local Offer Store

This workflow keeps TCB purchase requirements in Redis to reduce response size
and latency. It is an optional optimization; the standard integration is in
[Readme.JavaScript.md](Readme.JavaScript.md).

## 1. Install the SDK and Redis client

Package the SDK from `js/`, then install the archive and Redis client in your
application:

```bash
npm pack
npm install ./lib/thecouponbureau-basket-validator-1.0.0.tgz redis
```

> **Optional Redis setup**
>
> - macOS: `brew install redis && brew services start redis`
> - Ubuntu/Debian: `sudo apt install redis-server && sudo systemctl enable --now redis-server`
> - Windows: run Redis with Docker: `docker run -d --name tcb-redis -p 6379:6379 redis:7-alpine`

```javascript
const { createClient } = require("redis");
const {
    fetchAccessToken,
    localBasketValidation,
    parseScannedGs1s,
    redeemCoupons,
    rollbackCoupons,
    syncMasterOfferFiles,
    validateBasketHelper,
} = require("@thecouponbureau/basket-validator");

const redis = createClient({ url: process.env.REDIS_URL || "redis://localhost:6379" });
await redis.connect();
```

## 2. Get and securely cache a TCB token

```javascript
const baseUrl = "https://api.try.thecouponbureau.org";
const accessKey = process.env.TCB_ACCESS_KEY;
const accessToken = await fetchAccessToken(
    baseUrl,
    accessKey,
    process.env.TCB_SECRET_KEY
);
```

The access token is valid for 24 hours. Store it in your secure application
cache and refresh it before expiry.

## 3. Synchronize offer records into Redis

Use `initial` for the first six-month load and `incremental` for regular updates
from yesterday through tomorrow. Continue until `next_page_no` is `-1`.

```javascript
async function syncOffers(mode) {
    let pageNo = "";

    do {
        const page = await syncMasterOfferFiles(
            baseUrl,
            accessKey,
            accessToken,
            mode,
            pageNo
        );

        const writes = page.data.map(({ base_gs1, purchase_requirement }) =>
            redis.set(`tcb:mof:${base_gs1}`, JSON.stringify(purchase_requirement))
        );
        await Promise.all(writes);
        pageNo = page.next_page_no;
    } while (pageNo && pageNo !== "-1");
}

await syncOffers("initial");
```

The SDK retries `syncmof` 5xx responses after 10 and 20 seconds. For scheduled
updates, run `syncOffers("incremental")` and persist each returned page.

## 4. Resolve scanned values and load requirements

Serialized coupons are parsed locally. A 16-digit fetch code is sent as its own
TCB request and may expand into multiple serialized coupons. Locally parsed
coupons return `validated: false`; coupons returned by TCB return
`validated: true`. Input order is preserved.

```javascript
async function buildCouponsFromLocalStore(scannedGs1s) {
    const resolved = await parseScannedGs1s(
        baseUrl,
        accessKey,
        accessToken,
        scannedGs1s,
        "retailer"
    );

    const coupons = await Promise.all(
        resolved.map(async ({ gs1, base_gs1, validated }) => {
            const json = await redis.get(`tcb:mof:${base_gs1}`);
            if (!json) return null;
            return {
                gs1,
                purchase_requirement: JSON.parse(json),
                validated,
            };
        })
    );

    return coupons.filter(Boolean);
}

const scannedGs1s = [
    "8112009988459000019133924009755364",
    "8112001234567890",
];
const coupons = await buildCouponsFromLocalStore(scannedGs1s);
```

## 5. Perform optional local eligibility checks

`localBasketValidation` never calls TCB. Every coupon must contain a purchase
requirement.

```javascript
const basket = [
    { product_code: "037000930396", price: 1.29, quantity: 2, unit: "item" },
    { product_code: "037000934677", price: 1.34, quantity: 2, unit: "item" },
];

const localResult = localBasketValidation({ basket, coupons });
const locallyApplicable = new Set(
    localResult.applied_coupons.map((coupon) => coupon.coupon_code)
);
const eligibleCoupons = coupons.filter((coupon) => locallyApplicable.has(coupon.gs1));
```

This early check can discard coupons that cannot apply to the current basket.
It does not confirm TCB coupon status and does not redeem a coupon.

## 6. Confirm coupon status and calculate the final discount

Pass the eligible coupon objects to `validateBasketHelper`. Coupons already
marked `validated: true` skip the preprocessing call. Other coupons are checked
with TCB; coupons absent from `newly_redeemed` are removed. The final local
calculation uses the supplied purchase requirements.

```javascript
const validation = await validateBasketHelper({
    basket,
    coupons: eligibleCoupons,
    tcb_base_url: baseUrl,
    tcb_access_key: accessKey,
    tcb_access_token: accessToken,
    mode: "retailer",
});

console.log(JSON.stringify(validation, null, 2));
```

## 7. Complete checkout, redeem, or roll back

After the transaction succeeds, redeem only applied coupons:

```javascript
const appliedGs1s = validation.applied_coupons.map((item) => item.coupon_code);
const redemption = appliedGs1s.length
    ? await redeemCoupons(baseUrl, accessKey, accessToken, appliedGs1s, "retailer")
    : null;
```

If the redeemed transaction is voided within 60 minutes:

```javascript
const rollback = await rollbackCoupons(
    baseUrl,
    accessKey,
    accessToken,
    redeemedGs1s,
    "retailer"
);
```

Store redemption responses with the transaction and reconcile partial failures
using `newly_redeemed` rather than assuming every submitted coupon succeeded.
