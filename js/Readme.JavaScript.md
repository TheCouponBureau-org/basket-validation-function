# JavaScript Integration

Use the SDK to validate scanned coupons, calculate the basket discount, finalize
redemption after checkout, and roll back coupons when a transaction is voided.
Internet access to TCB is required. Node.js 18 or newer is required.

## 1. Add the SDK

Create a package archive from this `js/` directory:

```bash
npm pack
```

Copy the generated `.tgz` file into your application and install it:

```bash
npm install ./lib/thecouponbureau-basket-validator-1.0.0.tgz
```

Import the public functions:

```javascript
const {
    fetchAccessToken,
    redeemCoupons,
    rollbackCoupons,
    validateBasketHelper,
} = require("@thecouponbureau/basket-validator");
```

## 2. Get an access token

Keep the access key and secret key in secure application configuration. The
token is valid for 24 hours; cache it securely and refresh it before expiry.

```javascript
const baseUrl = "https://api.try.thecouponbureau.org";
const accessKey = process.env.TCB_ACCESS_KEY;
const accessToken = await fetchAccessToken(
    baseUrl,
    accessKey,
    process.env.TCB_SECRET_KEY
);
```

## 3. Validate the basket

Pass each scanned value as a coupon string. The SDK validates the coupons with
TCB, obtains their purchase requirements, preserves their input order, and
calculates the applicable discount.

```javascript
const input = {
    basket: [
        {
            product_code: "037000930396",
            price: 1.29,
            quantity: 2,
            unit: "item",
        },
        {
            product_code: "037000934677",
            price: 1.34,
            quantity: 2,
            unit: "item",
        },
    ],
    coupons: [
        "8112009988459000019133924009755364",
        "8112209988459000350001",
    ],
    tcb_base_url: baseUrl,
    tcb_access_key: accessKey,
    tcb_access_token: accessToken,
    mode: "retailer",
};

const validation = await validateBasketHelper(input);
console.log(JSON.stringify(validation, null, 2));
```

Example response:

```json
{
  "discount_in_cents": 200,
  "applied_coupons": [
    {
      "coupon_code": "8112009988459000019133924009755364",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": ["037000930396"]
      }
    },
    {
      "coupon_code": "8112209988459000359165266614604064",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": ["037000934677"]
      }
    }
  ]
}
```

An empty `applied_coupons` array means no coupon discount should be applied.
Validation does not finalize redemption.

Set `enable_logging: true` on the input to print the redacted validation input,
TCB preprocessing request and response, and resolved coupons as pretty JSON.

## 4. Apply the discount and redeem

After the transaction succeeds, redeem only the coupon codes returned in
`applied_coupons`:

```javascript
const appliedGs1s = validation.applied_coupons.map(
    (coupon) => coupon.coupon_code
);

if (appliedGs1s.length > 0) {
    const redemption = await redeemCoupons(
        baseUrl,
        accessKey,
        accessToken,
        appliedGs1s,
        input.mode,
        input.retailer_email_domain
    );
    console.log(JSON.stringify(redemption, null, 2));
}
```

The SDK sends at most 15 coupons per request, runs multiple requests in
parallel, and generates one `client_txn_id` per request. Retries reuse the same
ID for idempotency. Check `status`, `status_code`, and `newly_redeemed` before
treating every coupon as redeemed.

## 5. Roll back a voided transaction

Use the serialized GS1 values confirmed by the redemption response:

```javascript
const rollbackResponses = await rollbackCoupons(
    baseUrl,
    accessKey,
    accessToken,
    redeemedGs1s,
    input.mode
);
console.log(JSON.stringify(rollbackResponses, null, 2));
```

Rollback requests run in parallel. Rollback is allowed within 60 minutes of
redemption; do not call it for a successful transaction.

## Retailer or accelerator mode

Omit `mode`, leave it blank, or use `"retailer"` for retailer integration. For
accelerator integration, use:

```javascript
input.mode = "accelerator";
input.retailer_email_domain = "retailer.example";
```

Accelerator validation and redemption require `retailer_email_domain`.
Rollback requires only the mode.
