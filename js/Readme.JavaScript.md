# JavaScript Integration

Send the basket and scanned coupon codes to `validateBasketHelper(...)`; the
SDK obtains coupon purchase requirements from TCB and calculates the discount
inside your application. Internet access to TCB is required.

For the optional local offer-store workflow, see
[JavaScript with a local offer store](ReadmeWithLocalDatabase.JavaScript.md).

## 1. Add the SDK

Node.js 18 or newer is required. From the `js/` directory, create the package:

```bash
npm pack
```

Copy the generated archive into your application and install it:

```bash
npm install ./lib/thecouponbureau-basket-validator-1.0.0.tgz
```

Import the SDK functions used during checkout:

```javascript
const {
    fetchAccessToken,
    redeemCoupons,
    rollbackCoupons,
    validateBasketHelper,
} = require("@thecouponbureau/basket-validator");
```

## 2. Validate a basket

The example uses the TCB test environment. Keep credentials in secure
application configuration. Basket prices are per-unit currency amounts;
discounts are returned in cents.

```javascript
const baseUrl = "https://api.try.thecouponbureau.org";
const accessKey = process.env.TCB_ACCESS_KEY;
const accessToken = await fetchAccessToken(
    baseUrl,
    accessKey,
    process.env.TCB_SECRET_KEY
);

const input = {
    basket: [
        {
            product_code: "037000930396",
            price: 1.29,
            quantity: 2,
            unit: "item",
        },
    ],
    coupons: [
        { gs1: "8112009988459000019133924009755364" },
    ],
    tcb_base_url: baseUrl,
    tcb_access_key: accessKey,
    tcb_access_token: accessToken,
    mode: "retailer",
};

const validation = await validateBasketHelper(input);
console.log(JSON.stringify(validation, null, 2));

const appliedGs1s = validation.applied_coupons.map(
    (coupon) => coupon.coupon_code
);
```

Leave `purchase_requirement` and `validated` unset in the standard flow. The
SDK validates the coupon with TCB and obtains the current purchase requirement.
Fetch codes can also be supplied in `gs1`; no separate parsing call is needed.

Coupon strings are also accepted for compatibility:

```javascript
coupons: ["8112009988459000019133924009755364"]
```

Validation does **not** finalize redemption. An empty `applied_coupons` array
means no coupon discount should be applied. API and invalid-input failures are
thrown and must be handled before applying a discount.

Set `enable_logging: true` to print the redacted input, TCB preprocessing
request and response, and resolved coupons as formatted JSON.

### Sample input and output

Input:

```json
{
  "basket": [
    {
      "product_code": "037000930396",
      "price": 1.29,
      "quantity": 2,
      "unit": "item"
    }
  ],
  "coupons": [
    { "gs1": "8112009988459000019133924009755364" }
  ],
  "tcb_base_url": "https://api.try.thecouponbureau.org",
  "tcb_access_key": "YOUR_ACCESS_KEY",
  "tcb_access_token": "YOUR_ACCESS_TOKEN",
  "mode": "retailer"
}
```

Assuming TCB accepts the coupon and returns an offer for $1 off qualifying
items, the basket validation output is:

```json
{
  "discount_in_cents": 100,
  "applied_coupons": [
    {
      "coupon_code": "8112009988459000019133924009755364",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": ["037000930396"]
      }
    }
  ]
}
```

This is the validation output, not the final redemption response.

## 3. Complete checkout and redeem

Apply `validation.discount_in_cents`. After the transaction succeeds, redeem
only the coupon codes returned in `applied_coupons`:

```javascript
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
    // Save the redemption response with the POS transaction.
}
```

Check `status`, `status_code`, and `newly_redeemed` before treating every
coupon as redeemed. The SDK sends at most 15 coupons per request, executes
multiple requests concurrently, and generates one stable `client_txn_id` per
request so retries remain idempotent.

## 4. Roll back only when a redeemed transaction is voided

Use the coupon codes confirmed in `newly_redeemed`, saved as `redeemedGs1s`:

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

Check every rollback response. Do not call rollback during successful checkout.
Rollback is allowed within 60 minutes of coupon redemption.

## Retailer or accelerator mode

Omit `mode`, leave it blank, or use `"retailer"` for retailer integration. For
accelerator integration, set these values before validation:

```javascript
input.mode = "accelerator";
input.retailer_email_domain = "retailer.example";
```

Accelerator validation, fetch-code resolution, and final redemption use the
accelerator route and require a nonblank `retailer_email_domain`. Pass the same
mode and domain to `redeemCoupons(...)`. Rollback takes only the mode.
