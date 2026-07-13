# Kotlin integration flow

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
your-kotlin-project/lib/basket-validator-1.0-SNAPSHOT-all.jar
```

After that, add the JAR from your `lib/` folder to your Kotlin project classpath using your normal build setup.

## 3. Step-by-step integration

This walkthrough uses real serialized coupon examples and `base_gs1` values from `java/POS_Basket_Validation_UseCases.xlsx`.

The `16`-digit fetch code below is illustrative. The workbook contains serialized coupon examples and offer data, but not the fetch-code-to-coupon mapping returned by TCB.

#### Step 1. Customer scans four serialized coupons and one fetch code

| Scan order | Type | Scanned value |
| --- | --- | --- |
| 1 | Serialized coupon | `8112009988459000019133924009755364` |
| 2 | Serialized coupon | `8112009988459000039133772240739897` |
| 3 | Serialized coupon | `8112009988459000049133939957096441` |
| 4 | Serialized coupon | `8112009988459000199133935966961409` |
| 5 | 16-digit fetch code | `8112209988459000` |

#### Step 2. Resolve scanned values into serialized coupons and `base_gs1`

Request:

```kotlin
val resolved = TcbScannedGs1Service.parseScannedGs1s(
    "https://api.try.thecouponbureau.org/",
    "YOUR_ACCESS_KEY",
    accessToken,
    listOf(
        "8112009988459000019133924009755364",
        "8112009988459000039133772240739897",
        "8112009988459000049133939957096441",
        "8112009988459000199133935966961409",
        "8112209988459000"
    )
)
```

- The first four scanned values already start with `8112`, so `parseScannedGs1s(...)` parses them locally.
- The `16`-digit fetch code is sent to TCB in its own redemption request.
- Assume TCB returns the following additional serialized coupons from that fetch code.

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

#### Step 3. Load purchase requirements from the local `base_gs1` database

Use `base_gs1` as the key into your local offer / purchase-requirement database.

Response from local DB lookup:

| `base_gs1` | Workbook offer summary |
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

#### Step 4. Build the basket and perform local rejection first

Request basket:

Basket example:

| Product code | Qty | Price |
| --- | --- | --- |
| `037000930396` | 1 | `1.29` |
| `037000934677` | 1 | `1.34` |
| `030772076835` | 2 | `3.07` |
| `037000534358` | 1 | `6.62` |
| `037000808893` | 1 | `5.64` |
| `7106919588011` | 1 | `1.81` |
| `8952803493171` | 1 | `4.67` |

Local rejection examples before token fetch:

| Coupon | Why rejected locally |
| --- | --- |
| `8112009988459000199133935966961409` | `base_gs1 = 811200998845900019` needs `1A + 2B + 3C`; this basket has no third-group triplet |
| `8112009988459000139133621151540206` | `base_gs1 = 811200998845900013` needs spend across chips and dip and soda; this basket does not satisfy all groups |
| `8112009988459000089133401940529627` | `base_gs1 = 811200998845900008` needs 5 items from A and 2 free from B; this basket does not satisfy the quantity rule |

Call `validateBasketHelper(...)` one coupon at a time in this step.

Important:

- Do **not** set `tcbBaseUrl`
- Do **not** set `tcbAccessKey`
- Do **not** set `tcbAccessToken`

That makes `validateBasketHelper(...)` run as a local-only validation pass using only the basket and the locally loaded `purchase_requirement`.

Request:

```kotlin
import org.thecouponbureau.validate.basket.core.BasketValidator
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon

val locallyEligibleCoupons = mutableListOf<InputCoupon>()

for (coupon in coupons) {
    val localInput = BasketValidationInput().apply {
        this.basket = basket
        this.coupons = mutableListOf(coupon)
    }

    val localResult = BasketValidator.validateBasketHelper(localInput)

    if (localResult.error != null) {
        continue
    }

    if (localResult.basketValidationOutput != null
        && localResult.basketValidationOutput.discountInCents > 0
    ) {
        locallyEligibleCoupons.add(coupon)
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

Coupons kept after local filtering for the second pass:

- `8112009988459000019133924009755364`
- `8112009988459000039133772240739897`
- `8112009988459000049133939957096441`

#### Step 5. Build the validation input

In this second pass, send only `gs1` values in `coupons`.

Do not send `purchase_requirement` here.

Reason:

- `validateBasketHelper(...)` will call TCB `retailer/redeem` with `pre_process = "yes"`
- that TCB call validates whether the coupon is currently usable
- the SDK then uses the updated `purchase_requirement` returned by TCB for final basket validation

Request:

```kotlin
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon

val basket = mutableListOf(
    BasketItem().apply {
        productCode = "037000930396"
        price = 1.29
        quantity = 1
        unit = "item"
    },
    BasketItem().apply {
        productCode = "037000934677"
        price = 1.34
        quantity = 1
        unit = "item"
    },
    BasketItem().apply {
        productCode = "030772076835"
        price = 3.07
        quantity = 2
        unit = "item"
    },
    BasketItem().apply {
        productCode = "037000534358"
        price = 6.62
        quantity = 1
        unit = "item"
    },
    BasketItem().apply {
        productCode = "037000808893"
        price = 5.64
        quantity = 1
        unit = "item"
    }
)

val coupons = locallyEligibleCoupons.map { localCoupon ->
    InputCoupon().apply {
        this.gs1 = localCoupon.gs1
    }
}.toMutableList()

val input = BasketValidationInput().apply {
    this.basket = basket
    this.coupons = coupons
}
```

Resulting input payload shape:

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

#### Step 6. Get the TCB token

Request:

```kotlin
val accessToken = org.thecouponbureau.validate.basket.Services.TcbTokenService.fetchAccessToken(
    "https://api.try.thecouponbureau.org",
    "YOUR_ACCESS_KEY",
    "YOUR_SECRET_KEY"
)
```

Response:

```json
{
  "status": "success",
  "x-access-token": "YOUR_ACCESS_TOKEN"
}
```

#### Step 7. Call `validateBasketHelper(...)`

Request:

```kotlin
input.tcbBaseUrl = "https://api.try.thecouponbureau.org"
input.tcbAccessKey = "YOUR_ACCESS_KEY"
input.tcbAccessToken = accessToken

val result = BasketValidator.validateBasketHelper(input)
```

What happens inside this second validation pass:

1. TCB `retailer/redeem` is called with `pre_process = "yes"`.
2. TCB validates whether each coupon is currently usable.
3. Coupons not returned in `newly_redeemed` are removed.
4. The SDK uses the updated `purchase_requirement` returned by TCB.
5. Final basket validation runs on the TCB-confirmed coupon set.

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

#### Step 8. Apply the discount

Use `result.basketValidationOutput.discountInCents` as the transaction discount.

Response used by POS:

```json
{
  "discount_in_cents": 300
}
```

#### Step 9. Redeem coupons in TCB after discount application

Request:

```kotlin
val redeemResponseJson =
    org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService.redeemCoupons(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        listOf(
            "8112009988459000019133924009755364",
            "8112009988459000039133772240739897",
            "8112009988459000049133939957096441"
        )
    )
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

#### Step 10. Roll back redeemed coupons if the transaction is voided

Request:

```kotlin
val rollbackResponses =
    org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService.rollbackCoupons(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        listOf(
            "8112009988459000019133924009755364",
            "8112009988459000039133772240739897",
            "8112009988459000049133939957096441"
        )
    )
```

Response:

```json
{
  "8112009988459000019133924009755364": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000039133772240739897": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000049133939957096441": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}"
}
```

## 6. Resolve scanned GS1s into serialized GS1 + base GS1

Use:

- `TcbScannedGs1Service.parseScannedGs1s(...)`

This method:

- accepts a list of scanned GS1 strings
- returns `gs1` and `base_gs1`
- if a scanned code is a valid serialized GS1, it is parsed locally with no TCB call
- if concatenated serialized GS1s are provided, each serialized GS1 is parsed locally with no TCB call
- only `16` digit scanned codes are sent to TCB `retailer/redeem`
- each `16` digit scanned code is sent in its own redemption request
- all redemption calls run in parallel
- TCB calls send `no_purchase_requirement = "yes"` to avoid returning purchase requirement data
- for TCB calls, only coupons present in `newly_redeemed` are returned
- does not return `purchase_requirement`

Kotlin example:

```kotlin
import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service
import org.thecouponbureau.validate.basket.Services.TcbTokenService

fun main() {
    val accessToken = TcbTokenService.fetchAccessToken(
        "https://api.try.thecouponbureau.org/",
        "YOUR_ACCESS_KEY",
        "YOUR_SECRET_KEY"
    )

    val resolved = TcbScannedGs1Service.parseScannedGs1s(
        "https://api.try.thecouponbureau.org/",
        "YOUR_ACCESS_KEY",
        accessToken,
        listOf(
            "8112209988459000329165266614604064",
            "8112209988459000340001"
        )
    )

    resolved.forEach { item ->
        println("${item.gs1} -> ${item.baseGs1}")
    }
}
```

Example resolve JSON response:

```json
[
  {
    "gs1": "8112209988459000329165266614604064",
    "base_gs1": "811220998845900032"
  },
  {
    "gs1": "8112109988459000269133321426026193",
    "base_gs1": "811210998845900026"
  },
  {
    "gs1": "8112109988459000269133587761214614",
    "base_gs1": "811210998845900026"
  }
]
```
