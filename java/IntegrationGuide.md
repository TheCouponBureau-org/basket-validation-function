# SDK as Code Integration Guide

![Integration Flow](flow.png)

This guide is based on `Sdk as Code Integration Guide.odt` and aligned with the current Java SDK behavior in this repository.

## 1. Build the JAR

From the `java/` folder:

```bash
./build-jar.sh
```

Use the fat JAR for integration:

```bash
target/basket-validator-1.0-SNAPSHOT-all.jar
```

## 2. Add the JAR to your project

Copy the fat JAR into your application, for example:

```bash
your-project/lib/basket-validator-1.0-SNAPSHOT-all.jar
```

Then add that JAR from your `lib/` folder to your project classpath using your normal build setup.

## 3. Authentication

After logging in to the TCB Portal, generate:

- `Access Key`
- `Secret Key`

Use those credentials to obtain a TCB access token. The token is valid for 24 hours.

Request:

```java
String accessToken = org.thecouponbureau.validate.basket.Services.TcbTokenService.fetchAccessToken(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        "YOUR_SECRET_KEY");
```

Response:

```json
{
  "status": "success",
  "x-access-token": "YOUR_ACCESS_TOKEN"
}
```

If you want the full token response object:

```java
TcbTokenService.AccessTokenResponse tokenResponse =
        TcbTokenService.fetchAccessTokenResponse(
                "https://api.try.thecouponbureau.org",
                "YOUR_ACCESS_KEY",
                "YOUR_SECRET_KEY");
```

## 4. Sync Master Offer Files into the local database

Retailer should store TCB Master Offer File / Purchase Requirement data locally and key it by `base_gs1`.

Recommended storage model:

- key = `base_gs1`
- value = full `purchase_requirement` JSON object

The `purchase_requirement` JSON should be stored exactly as received.

Example MOF / purchase requirement payload:

```json
{
  "base_gs1": "8112010037000440787",
  "description": "OFF ONE Oral-B Glide Manual Floss OR Oral-B Expanding Floss OR Oral-B Glide Floss Picks OR Satin Floss",
  "primary_purchase_gtins": [
    "300410100391",
    "300410605513",
    "300410605520",
    "300410825850",
    "037000038665"
  ],
  "primary_purchase_save_value": 100,
  "primary_purchase_requirements": 1,
  "primary_purchase_req_code": 0,
  "save_value_code": 0
}
```

## 5. Resolve scanned 8112 coupons into serialized GS1 values and `base_gs1`

Supported coupon formats:

- single serialized GS1 coupon
- concatenated serialized GS1 coupons
- 16-digit fetch code

Use `parseScannedGs1s(...)` to resolve scanned values into serialized coupons and `base_gs1`.

Important:

- valid serialized GS1 values are parsed locally
- concatenated serialized GS1 values are parsed locally
- only 16-digit fetch codes go to TCB
- each 16-digit fetch code is sent in its own TCB redemption request
- TCB requests use `no_purchase_requirement = "yes"`
- only `newly_redeemed` coupons are returned from TCB-backed fetch-code resolution

Example scanned inputs:

| Scan order | Type | Scanned value |
| --- | --- | --- |
| 1 | Serialized coupon | `8112009988459000019133924009755364` |
| 2 | Serialized coupon | `8112009988459000039133772240739897` |
| 3 | Serialized coupon | `8112009988459000049133939957096441` |
| 4 | Serialized coupon | `8112009988459000199133935966961409` |
| 5 | 16-digit fetch code | `8112209988459000` |

Request:

```java
List<TcbScannedGs1Service.SerializedGs1Data> resolved =
        TcbScannedGs1Service.parseScannedGs1s(
                "https://api.try.thecouponbureau.org/",
                "YOUR_ACCESS_KEY",
                accessToken,
                List.of(
                        "8112009988459000019133924009755364",
                        "8112009988459000039133772240739897",
                        "8112009988459000049133939957096441",
                        "8112009988459000199133935966961409",
                        "8112209988459000"));
```

Response:

| Source | Serialized coupon | `base_gs1` |
| --- | --- | --- |
| Local parse | `8112009988459000019133924009755364` | `811200998845900001` |
| Local parse | `8112009988459000039133772240739897` | `811200998845900003` |
| Local parse | `8112009988459000049133939957096441` | `811200998845900004` |
| Local parse | `8112009988459000199133935966961409` | `811200998845900019` |
| TCB fetch-code response | `8112009988459000019133520317194861` | `811200998845900001` |
| TCB fetch-code response | `8112009988459000039133690612006084` | `811200998845900003` |
| TCB fetch-code response | `8112009988459000049133457646689353` | `811200998845900004` |
| TCB fetch-code response | `8112009988459000059133286213033835` | `811200998845900005` |
| TCB fetch-code response | `8112009988459000089133401940529627` | `811200998845900008` |
| TCB fetch-code response | `8112009988459000119133614973675487` | `811200998845900011` |
| TCB fetch-code response | `8112009988459000129133212234898075` | `811200998845900012` |
| TCB fetch-code response | `8112009988459000139133621151540206` | `811200998845900013` |
| TCB fetch-code response | `8112009988459000149133342361220548` | `811200998845900014` |
| TCB fetch-code response | `8112009988459000199133782272284945` | `811200998845900019` |

## 6. Load purchase requirements from the local `base_gs1` database

Use the resolved `base_gs1` to fetch purchase requirements from your local DB.

Example `base_gs1` lookup set from `POS_Basket_Validation_UseCases.xlsx`:

| `base_gs1` | Offer summary |
| --- | --- |
| `811200998845900001` | Buy 2 Products in Group A and Save $1.00 |
| `811200998845900003` | Buy any 2 products from A or B and save $1.00 |
| `811200998845900004` | Buy any 2 products from A or B or C and save $1.00 |
| `811200998845900005` | Buy 1 get 1 free up to $1.99 |
| `811200998845900008` | Buy 5 Products in Group A and get 2 Free from Group B |
| `811200998845900011` | Buy 1 item from Group A get 1 item from Group B free up to $1.99 |
| `811200998845900012` | Spend $5 on chips OR dip OR soda and get $2 off |
| `811200998845900013` | Spend $5 on chips AND dip AND soda and get $3 off |
| `811200998845900014` | Spend $5 on chips AND dip OR soda and get $2 off |
| `811200998845900019` | Buy 1A and 2B and 3C and get $3 off |

Build coupon objects from resolved GS1 values and local purchase requirements:

```java
Map<String, PurchaseRequirement> purchaseRequirementDb = loadPurchaseRequirementDb();

List<InputCoupon> coupons = new ArrayList<>();
for (TcbScannedGs1Service.SerializedGs1Data item : resolved) {
    PurchaseRequirement purchaseRequirement =
            purchaseRequirementDb.get(item.baseGs1);

    if (purchaseRequirement == null) {
        continue;
    }

    InputCoupon coupon = new InputCoupon();
    coupon.gs1 = item.gs1;
    coupon.purchaseRequirement = purchaseRequirement;
    coupons.add(coupon);
}
```

## 7. Perform local-only coupon rejection first

Before doing the TCB-backed validation pass, run a local-only pass one coupon at a time.

Important:

- do **not** set `tcbBaseUrl`
- do **not** set `tcbAccessKey`
- do **not** set `tcbAccessToken`

This makes `validateBasketHelper(...)` run locally using only:

- basket
- `gs1`
- locally loaded `purchase_requirement`

Example basket:

| Product code | Qty | Price |
| --- | --- | --- |
| `037000930396` | 1 | `1.29` |
| `037000934677` | 1 | `1.34` |
| `030772076835` | 2 | `3.07` |
| `037000534358` | 1 | `6.62` |
| `037000808893` | 1 | `5.64` |
| `7106919588011` | 1 | `1.81` |
| `8952803493171` | 1 | `4.67` |

Request:

```java
List<InputCoupon> locallyEligibleCoupons = new ArrayList<>();

for (InputCoupon coupon : coupons) {
    BasketValidationInput localInput = new BasketValidationInput();
    localInput.basket = basket;
    localInput.coupons = List.of(coupon);

    ValidationResult localResult = BasketValidator.validateBasketHelper(localInput);

    if (localResult.error != null) {
        continue;
    }

    if (localResult.basketValidationOutput != null
            && localResult.basketValidationOutput.discountInCents > 0) {
        locallyEligibleCoupons.add(coupon);
    }
}
```

Response:

```json
{
  "eligible_coupon_gs1s": [
    "8112009988459000019133924009755364",
    "8112009988459000039133772240739897",
    "8112009988459000049133939957096441"
  ],
  "rejected_coupon_gs1s": [
    "8112009988459000199133935966961409",
    "8112009988459000139133621151540206",
    "8112009988459000089133401940529627"
  ]
}
```

## 8. Build the final validation input

In the second pass, send only `gs1` values in `coupons`.

Do not send `purchase_requirement` in this step.

Reason:

- `validateBasketHelper(...)` will call TCB `retailer/redeem` with `pre_process = "yes"`
- TCB validates whether each coupon is currently usable
- the SDK uses the updated `purchase_requirement` returned by TCB for final basket validation

Request:

```java
List<InputCoupon> couponsForFinalValidation = new ArrayList<>();
for (InputCoupon localCoupon : locallyEligibleCoupons) {
    InputCoupon coupon = new InputCoupon();
    coupon.gs1 = localCoupon.gs1;
    couponsForFinalValidation.add(coupon);
}

BasketValidationInput input = new BasketValidationInput();
input.basket = basket;
input.coupons = couponsForFinalValidation;
```

Input payload shape:

```json
{
  "basket": [
    { "product_code": "037000930396", "price": 1.29, "quantity": 1, "unit": "item" },
    { "product_code": "037000934677", "price": 1.34, "quantity": 1, "unit": "item" },
    { "product_code": "030772076835", "price": 3.07, "quantity": 2, "unit": "item" },
    { "product_code": "037000534358", "price": 6.62, "quantity": 1, "unit": "item" },
    { "product_code": "037000808893", "price": 5.64, "quantity": 1, "unit": "item" }
  ],
  "coupons": [
    { "gs1": "8112009988459000019133924009755364" },
    { "gs1": "8112009988459000039133772240739897" },
    { "gs1": "8112009988459000049133939957096441" }
  ]
}
```

## 9. Get the applicable discount

Request:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org";
input.tcbAccessKey = "YOUR_ACCESS_KEY";
input.tcbAccessToken = accessToken;

ValidationResult result = BasketValidator.validateBasketHelper(input);
```

What happens inside this call:

1. TCB `retailer/redeem` is called with `pre_process = "yes"`
2. TCB validates whether each coupon is currently usable
3. Coupons not returned in `newly_redeemed` are removed
4. The SDK uses the updated `purchase_requirement` returned by TCB
5. Final basket validation runs on the TCB-confirmed coupon set

Response:

```json
{
  "basket_validation_output": {
    "discount_in_cents": 300,
    "applied_coupons": [
      {
        "coupon_code": "8112009988459000019133924009755364",
        "face_value_in_cents": 100,
        "product_codes": {
          "primary": [
            "037000930396",
            "037000934677"
          ]
        }
      },
      {
        "coupon_code": "8112009988459000039133772240739897",
        "face_value_in_cents": 100,
        "product_codes": {
          "secondary": [
            "030772076835"
          ]
        }
      },
      {
        "coupon_code": "8112009988459000049133939957096441",
        "face_value_in_cents": 100,
        "product_codes": {
          "third": [
            "037000534358",
            "037000808893"
          ]
        }
      }
    ]
  },
  "error": null
}
```

Interpretation:

- `discount_in_cents` = total discount to apply
- `applied_coupons` = coupons that should be attached to the transaction
- `product_codes.primary` = products that satisfied primary requirements
- `product_codes.secondary` = products that satisfied secondary requirements
- `product_codes.third` = products that satisfied third-level requirements
- `error` is diagnostic and can be ignored during normal successful checkout handling

## 10. Apply the discount

Use:

```json
{
  "discount_in_cents": 300
}
```

as the transaction discount result from the SDK.

For each item in `applied_coupons`:

- attach the coupon to the matching products in `product_codes`
- apply `face_value_in_cents` as the manufacturer coupon discount

## 11. Anti-stacking

The SDK already includes anti-stacking across 8112 coupons.

As each coupon is applied:

- qualifying basket items are consumed
- remaining coupons are evaluated against the reduced basket

No extra anti-stacking logic is required inside the 8112 coupon engine.

If retailer already has its own digital coupon platform, a simple cross-engine anti-stacking approach is:

1. run the retailer’s existing digital coupon engine first
2. identify products that already consumed retailer coupon value
3. remove those products from the basket
4. pass the reduced basket and 8112 coupons into this SDK

This prevents overlap:

- between retailer digital coupons and 8112 coupons
- between multiple 8112 coupons

## 12. Post-transaction coupon redemption

After discount application and successful transaction completion, redeem every applied 8112 coupon in TCB.

Request:

```java
String redeemResponseJson =
        TcbCouponRedeemService.redeemCoupons(
                "https://api.try.thecouponbureau.org",
                "YOUR_ACCESS_KEY",
                accessToken,
                List.of(
                        "8112009988459000019133924009755364",
                        "8112009988459000039133772240739897",
                        "8112009988459000049133939957096441"));
```

Response:

```json
{
  "status": "success",
  "status_code": "FULL_REDEMPTION",
  "newly_redeemed": [
    {
      "gs1": "8112009988459000019133924009755364",
      "master_offer_file": "811200998845900001"
    },
    {
      "gs1": "8112009988459000039133772240739897",
      "master_offer_file": "811200998845900003"
    },
    {
      "gs1": "8112009988459000049133939957096441",
      "master_offer_file": "811200998845900004"
    }
  ],
  "total_gs1s_processed": 3,
  "message": "Redeemed 3 gs1(s)"
}
```

Notes:

- redemption uses TCB `retailer/redeem` without `pre_process`
- the SDK generates `client_txn_id` internally for idempotency
- if the same request is retried due to timeout or network interruption, the same `client_txn_id` is reused for that retry path

## 13. Rollback redeemed coupons if needed

If the transaction is voided or reversed, call rollback.

Request:

```java
Map<String, String> rollbackResponses =
        TcbCouponRollbackService.rollbackCoupons(
                "https://api.try.thecouponbureau.org",
                "YOUR_ACCESS_KEY",
                accessToken,
                List.of(
                        "8112009988459000019133924009755364",
                        "8112009988459000039133772240739897",
                        "8112009988459000049133939957096441"));
```

Response:

```json
{
  "8112009988459000019133924009755364": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000039133772240739897": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000049133939957096441": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}"
}
```

## 14. Clearing and settlement

After successful redemption:

- retailer can generate the industry-standard coupon redemption file
- retailer can send that file to the clearinghouse

Because coupons were already authenticated, validated, and redeemed through TCB, the redemption file becomes the settlement record for manufacturer reimbursement.

## 15. Retry behavior for TCB API calls

All SDK TCB API calls use shared retry logic.

Backoff sequence:

- `50 ms`
- `100 ms`
- `200 ms`

Total attempts:

- `4`
- 1 initial request
- up to 3 retries

This retry logic is shared across:

- token fetch
- fetch-code resolution
- coupon validation resolution
- coupon redemption
- coupon rollback
