# Kotlin integration flow

## High Level Architecture

![High Level Architecture](highl.png)

<br/>
<br/>

## Sequence Diagram

![Sequence Diagram](flow.png)

## 1. Build the JAR

From the `java/` folder:

```bash
./build-jar.sh
```

Use the fat JAR for integration:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
```

## 2. Add the JAR to your project

Copy the fat JAR into your application, for example:

```bash
your-kotlin-project/lib/basket-validator-1.0-SNAPSHOT.jar
```

After that, add the JAR from your `lib/` folder to your Kotlin project classpath using your normal build setup.

## 3. Sync MOF purchase requirements into your server

Use `TcbMofSyncService.syncMasterOfferFiles(...)` to pull Master Offer File purchase requirements into your database so basket validation can use local purchase requirements instead of waiting on live MOF lookups.

Request:

```kotlin
import org.thecouponbureau.validate.basket.Services.TcbMofSyncService

val mofResponse = TcbMofSyncService.syncMasterOfferFiles(
    "https://api.portal.thecouponbureau.org",
    "YOUR_ACCESS_KEY",
    accessToken,
    "initial",
    ""
)

println("nextPageNo = ${mofResponse.nextPageNo}")

for (record in mofResponse.data) {
    println("base_gs1 = ${record.baseGs1}")
    println("primaryPurchaseGtins = ${record.purchaseRequirement.primaryPurchaseGtins}")
}
```

Example Redis storage pattern:

> **Optional: Install Redis locally before using the Redis examples**
>
> **macOS**
> 1. `brew install redis`
> 2. `brew services start redis`
> 3. Verify with `redis-cli ping`
>
> **Linux (Ubuntu/Debian)**
> 1. `sudo apt update`
> 2. `sudo apt install redis-server`
> 3. `sudo systemctl enable redis-server`
> 4. `sudo systemctl start redis-server`
> 5. Verify with `redis-cli ping`
>
> **Windows**
> 1. Install Docker Desktop
> 2. Run `docker run --name redis -p 6379:6379 -d redis`
> 3. Verify with `docker exec -it redis redis-cli ping`
>
> If Redis is not needed in your environment, skip this and use any other local database keyed by `base_gs1`.

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import redis.clients.jedis.Jedis

val mapper = ObjectMapper()

Jedis("localhost", 6379).use { jedis ->
    for (record in mofResponse.data) {
        val purchaseRequirementJson =
            mapper.writeValueAsString(record.purchaseRequirement)

        jedis.set(record.baseGs1, purchaseRequirementJson)
    }
}
```

Mode behavior:

- `initial` = last 6 months through today
- `incremental` = yesterday through today

Example date windows if today is `2026-08-17`:

- `initial` => `2026-02-17` through `2026-08-17`
- `incremental` => `2026-08-16` through `2026-08-17`

Returned shape:

- `data[].baseGs1`
- `data[].purchaseRequirement.primaryPurchaseGtins`
- `data[].purchaseRequirement.primaryPurchaseRequirements`
- `data[].purchaseRequirement.primaryPurchaseReqCode`
- `data[].purchaseRequirement.saveValueCode`
- and the other SDK-native `PurchaseRequirement` fields

Use `nextPageNo` to request the next page. When `nextPageNo = -1`, there are no more records.

Retry behavior for this helper:

- retries only on `5XX`
- first retry after `10` seconds
- second retry after `20` seconds

## 4. Step-by-step integration

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

#### Step 2. Get the TCB token

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

#### Step 3. Resolve scanned values into serialized coupons and `base_gs1`

Request:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service

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

val mapper = ObjectMapper().apply {
    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
}

println("Resolved scanned GS1 response:")
println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resolved))

resolved.forEach { item ->
    println(
        "serialized_gs1=${item.gs1}, base_gs1=${item.baseGs1}, validated=${item.validated}"
    )
}
```

- The first four scanned values already start with `8112`, so `parseScannedGs1s(...)` parses them locally.
- The `16`-digit fetch code is sent to TCB in its own redemption request.
- Assume TCB returns the following additional serialized coupons from that fetch code.

Response:

```json
[
  {
    "gs1": "8112009988459000019133924009755364",
    "base_gs1": "811200998845900001"
  },
  {
    "gs1": "8112009988459000039133772240739897",
    "base_gs1": "811200998845900003"
  },
  {
    "gs1": "8112009988459000049133939957096441",
    "base_gs1": "811200998845900004"
  },
  {
    "gs1": "8112009988459000199133935966961409",
    "base_gs1": "811200998845900019"
  },
  {
    "gs1": "8112009988459000019133520317194861",
    "base_gs1": "811200998845900001",
    "validated": true
  }
]
```

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

For TCB fetch-code results, `validated = true` means the coupon was already validated by TCB during fetch-code expansion.

#### Step 4. Load purchase requirements from the local `base_gs1` database

Use `base_gs1` as the key into your local offer / purchase-requirement database.

If you store purchase requirements in Redis, use:

- key = `base_gs1`
- value = serialized `purchase_requirement` JSON

Example Redis lookup:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement
import redis.clients.jedis.Jedis

val mapper = ObjectMapper()

Jedis("localhost", 6379).use { jedis ->
    for (item in resolved) {
        val purchaseRequirementJson = jedis.get(item.baseGs1) ?: continue

        val purchaseRequirement =
            mapper.readValue(
                purchaseRequirementJson,
                PurchaseRequirement::class.java
            )

        println(
            "gs1=${item.gs1}, base_gs1=${item.baseGs1}, validated=${item.validated}, " +
                "primaryPurchaseGtins=${purchaseRequirement.primaryPurchaseGtins}"
        )
    }
}
```

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

#### Step 5. Build coupon objects from resolved GS1 values and local purchase requirements

Request:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement
import redis.clients.jedis.Jedis

val scannedCoupons = listOf(
    "8112009988459000019133924009755364",
    "8112009988459000039133772240739897",
    "8112009988459000049133939957096441",
    "8112009988459000199133935966961409",
    "8112209988459000"
)

val resolved = TcbScannedGs1Service.parseScannedGs1s(
    "https://api.try.thecouponbureau.org/",
    "YOUR_ACCESS_KEY",
    accessToken,
    scannedCoupons
)

val mapper = ObjectMapper()

val coupons = mutableListOf<InputCoupon>()
Jedis("localhost", 6379).use { jedis ->
    for (item in resolved) {
        val purchaseRequirementJson = jedis.get(item.baseGs1) ?: continue

        val purchaseRequirement =
            mapper.readValue(
                purchaseRequirementJson,
                PurchaseRequirement::class.java
            )

        coupons.add(
            InputCoupon().apply {
                gs1 = item.gs1
                this.purchaseRequirement = purchaseRequirement
                validated = item.validated
            }
        )
    }
}
```

This sample resolves each scanned value to `gs1` + `base_gs1` using the SDK first, then reads the purchase requirement from Redis using `base_gs1` as the key.

If you want the Redis loading logic as a reusable helper, use:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement
import redis.clients.jedis.Jedis

fun loadPurchaseRequirementDb(): Map<String, PurchaseRequirement> {
    val mapper = ObjectMapper()
    val purchaseRequirements = mutableMapOf<String, PurchaseRequirement>()

    Jedis("localhost", 6379).use { jedis ->
        var cursor = "0"
        do {
            val scanResult = jedis.scan(cursor)
            cursor = scanResult.cursor

            for (baseGs1 in scanResult.result) {
                val purchaseRequirementJson = jedis.get(baseGs1) ?: continue

                purchaseRequirements[baseGs1] =
                    mapper.readValue(
                        purchaseRequirementJson,
                        PurchaseRequirement::class.java
                    )
            }
        } while (cursor != "0")
    }

    return purchaseRequirements
}
```

If you prefer to load Redis into memory first and then build coupons, use:

```kotlin
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement

val purchaseRequirementDb: Map<String, PurchaseRequirement> = loadPurchaseRequirementDb()

val coupons = mutableListOf<InputCoupon>()
for (item in resolved) {
    val purchaseRequirement = purchaseRequirementDb[item.baseGs1] ?: continue

    coupons.add(
        InputCoupon().apply {
            gs1 = item.gs1
            this.purchaseRequirement = purchaseRequirement
            validated = item.validated
        }
    )
}
```

Response:

```json
{
  "coupons": [
    {
      "gs1": "8112009988459000019133924009755364",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900001" }
    },
    {
      "gs1": "8112009988459000039133772240739897",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900003" }
    },
    {
      "gs1": "8112009988459000049133939957096441",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900004" }
    },
    {
      "gs1": "8112009988459000199133935966961409",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900019" }
    },
    {
      "gs1": "8112009988459000139133621151540206",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900013" }
    },
    {
      "gs1": "8112009988459000089133401940529627",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900008" }
    }
  ]
}
```

#### Step 6. Build the basket and perform local rejection first

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

Call `localBasketValidation(...)` one coupon at a time in this step.

Important:

This method does not take TCB credentials. It only uses the basket and the locally loaded `purchase_requirement`.

Request:

```kotlin
import org.thecouponbureau.validate.basket.core.BasketValidator
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon
import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput

val locallyEligibleCoupons = mutableListOf<InputCoupon>()

for (coupon in coupons) {
    val localInput = LocalBasketValidationInput().apply {
        this.basket = basket
        this.coupons = mutableListOf(coupon)
    }

    val localResult = BasketValidator.localBasketValidation(localInput)

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

#### Step 7. Build the validation input

In this second pass, send coupon objects in `coupons` with:

- `gs1`
- `purchase_requirement`
- optional `validated = true`

Optimization:

- if `validated = true`, `validateBasketHelper(...)` skips the TCB validation call for that coupon
- if `validated` is not `true`, `validateBasketHelper(...)` calls TCB `/retailer/redeem` (or `/accelerator/redeem` in accelerator mode) with:
  - `pre_process = "yes"`
  - `no_purchase_requirement = "yes"`
- coupons not returned in `newly_redeemed` are removed
- the remaining coupons already have local `purchase_requirement` objects, so the final discount is calculated locally

Request:

```kotlin
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput

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
        gs1 = localCoupon.gs1
        purchaseRequirement = localCoupon.purchaseRequirement
        validated = localCoupon.validated
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
    {
      "gs1": "8112009988459000019133924009755364",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900001" },
      "validated": true
    },
    {
      "gs1": "8112009988459000039133772240739897",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900003" }
    },
    {
      "gs1": "8112009988459000049133939957096441",
      "purchase_requirement": { "...": "loaded from local DB using 811200998845900004" }
    }
  ]
}
```

#### Configure retailer or accelerator mode

`validateBasketHelper` takes a `BasketValidationInput`. Configure the mode and
retailer email domain on that input before calling it:

```kotlin
input.mode = "accelerator"
input.retailerEmailDomain = "retailer.example"
```

- `mode = null`, blank, or `"retailer"` preserves retailer behavior.
- `mode = "accelerator"` uses `/accelerator/redeem` and requires a nonblank `retailerEmailDomain` for TCB calls.
- Modes are case-insensitive; unsupported values are rejected.
- Accelerator requests send the supplied domain unchanged as `retailer_email_domain`, alongside `gs1s` and the existing `pre_process = "yes"` during preprocessing.
- Retailer requests omit `retailer_email_domain`.
- Pass the same configuration to subsequent redemption and rollback calls; setting the input does not configure these separate service calls globally.

For JSON input, add these fields to the existing basket payload:

```json
{
  "mode": "accelerator",
  "retailer_email_domain": "retailer.example"
}
```

Direct preprocessing calls also accept `mode, retailerEmailDomain` as the
last two arguments: `TcbScannedGs1Service.parseScannedGs1s(...)`,
`TcbCouponResolutionService.resolveCoupons(..., enableLogging)`, and
`TcbCouponResolutionService.validateCoupons(..., enableLogging)`.
Original method signatures remain supported and default to retailer mode.

#### Step 8. Call `validateBasketHelper(...)`

Request:

```kotlin
input.tcbBaseUrl = "https://api.try.thecouponbureau.org"
input.tcbAccessKey = "YOUR_ACCESS_KEY"
input.tcbAccessToken = accessToken
input.mode = "accelerator"
input.retailerEmailDomain = "retailer.example"

val result = BasketValidator.validateBasketHelper(input)
```

What happens inside this second validation pass:

1. Coupons with `validated = true` are kept as already validated.
2. Coupons without `validated = true` are sent to TCB `/retailer/redeem` or `/accelerator/redeem`, according to `input.mode`.
3. That TCB request uses `pre_process = "yes"` and `no_purchase_requirement = "yes"`.
4. Coupons not returned in `newly_redeemed` are removed.
5. Final basket validation runs locally using the surviving coupons and their local `purchase_requirement` objects.

Response:

```json
{
  "discount_in_cents": 300,
  "applied_coupons": [
    {
      "coupon_code": "8112009988459000019133924009755364",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": [
          "037000930396",
          "037000934677"
        ]
      }
    },
    {
      "coupon_code": "8112009988459000039133772240739897",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": [
          "030772076835"
        ]
      }
    },
    {
      "coupon_code": "8112009988459000049133939957096441",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": [
          "037000534358",
          "037000808893"
        ]
      }
    }
  ]
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
        ),
        input.mode,
        input.retailerEmailDomain
    )
```

Final redemption omits `pre_process`. Accelerator redemption includes
`retailer_email_domain` in every batch.

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
        ),
        input.mode
    )
```

Rollback uses DELETE `/accelerator/rollback/:gs1` in accelerator mode or
`/retailer/rollback/:gs1` in retailer mode. No retailer email domain or request body is sent.

Response:

```json
{
  "8112009988459000019133924009755364": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000039133772240739897": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}",
  "8112009988459000049133939957096441": "{\"status\":\"success\",\"message\":\"Coupon rollback successful\"}"
}
```

## End-to-End Flow Diagram

```text
1. POS scans coupons and builds basket
   |
   v
2. Parse scanned GS1 values
   - serialized GS1s parsed locally
   - fetch codes expanded through TCB if needed
   |
   v
3. Use base_gs1 to load purchase requirements from local DB
   |
   v
4. Call localBasketValidation(...) one coupon at a time
   - drop coupons that are not basket-eligible locally
   |
   v
5. Build final validateBasketHelper(...) input
   - gs1
   - purchase_requirement
   - validated=true only for coupons already validated earlier
   |
   v
6. Call validateBasketHelper(...)
   - skips TCB for validated=true coupons
   - calls TCB retailer/redeem for remaining coupons
     with pre_process=yes and no_purchase_requirement=yes
   - removes coupons not returned in newly_redeemed
   - calculates final discount locally
   |
   v
7. POS applies discount to transaction
   |
   v
8. After transaction success, call redeemCoupons(...)
   |
   v
9. If transaction is voided later, call rollbackCoupons(...)
```

## Complete Kotlin Example

The following example hardcodes basket data and scanned coupon values, then uses the SDK to resolve `gs1 -> base_gs1` and loads purchase requirements from Redis.

```kotlin
1 package demo
2
3 import com.fasterxml.jackson.databind.ObjectMapper
4 import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService
5 import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService
6 import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service
7 import org.thecouponbureau.validate.basket.Services.TcbTokenService
8 import org.thecouponbureau.validate.basket.core.BasketValidator
9 import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon
10 import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem
11 import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput
12 import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon
13 import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput
14 import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement
15 import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult
16 import redis.clients.jedis.Jedis
17
18 object EndToEndBasketValidationExample {
19
20     @JvmStatic
21     fun main(args: Array<String>) {
22         val tcbBaseUrl = "https://api.try.thecouponbureau.org"
23         val tcbAccessKey = "YOUR_ACCESS_KEY"
24         val tcbSecretKey = "YOUR_SECRET_KEY"
25
26         val accessToken = TcbTokenService.fetchAccessToken(
27             tcbBaseUrl,
28             tcbAccessKey,
29             tcbSecretKey
30         )
31
32         val basket = buildBasket()
33         val couponsFromLocalDb = buildCouponsFromLocalDb(
34             tcbBaseUrl,
35             tcbAccessKey,
36             accessToken
37         )
38
39         val locallyEligibleCoupons = mutableListOf<InputCoupon>()
40
41         for (coupon in couponsFromLocalDb) {
42             val localInput = LocalBasketValidationInput().apply {
43                 this.basket = basket
44                 this.coupons = mutableListOf(coupon)
45             }
46
47             val localResult = BasketValidator.localBasketValidation(localInput)
48
49             if (localResult.error != null) {
50                 continue
51             }
52
53             if (localResult.basketValidationOutput != null
54                 && localResult.basketValidationOutput.discountInCents > 0
55             ) {
56                 locallyEligibleCoupons.add(coupon)
57             }
58         }
59
60         val validateInput = BasketValidationInput().apply {
61             this.basket = basket
62             this.coupons = locallyEligibleCoupons
63             this.tcbBaseUrl = tcbBaseUrl
64             this.tcbAccessKey = tcbAccessKey
65             this.tcbAccessToken = accessToken
66             this.mode = "accelerator"
67             this.retailerEmailDomain = "retailer.example"
68             this.enableLogging = true
69         }
70
71         val finalResult = BasketValidator.validateBasketHelper(validateInput)
72
73         println("discount_in_cents = ${finalResult.basketValidationOutput.discountInCents}")
74
75         for (appliedCoupon: AppliedCoupon in finalResult.basketValidationOutput.appliedCoupons) {
76             println("coupon_code = ${appliedCoupon.couponCode}")
77             println("face_value_in_cents = ${appliedCoupon.faceValueInCents}")
78             println("gtins = ${appliedCoupon.productCodes["gtins"]}")
79         }
80
81         val appliedCouponGs1s = finalResult.basketValidationOutput.appliedCoupons
82             .map { appliedCoupon -> appliedCoupon.couponCode }
83
84         // Transaction done in POS using finalResult.basketValidationOutput.discountInCents
85         // Only after transaction success should retailer redeem the applied coupons in TCB.
86
87         val redeemResponse = TcbCouponRedeemService.redeemCoupons(
88             tcbBaseUrl,
89             tcbAccessKey,
90             accessToken,
91             appliedCouponGs1s,
92             validateInput.mode,
93             validateInput.retailerEmailDomain
94         )
95
96         println("redeemResponse = $redeemResponse")
97
98         // If transaction is voided later, roll back those redeemed coupons.
99         val rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
100             tcbBaseUrl,
101             tcbAccessKey,
102             accessToken,
103             appliedCouponGs1s,
104             validateInput.mode
105         )
106
107         println("rollbackResponses = $rollbackResponses")
108     }
109
110     private fun buildBasket(): MutableList<BasketItem> {
111         return mutableListOf(
112             basketItem("037000930396", 1.29, 1),
113             basketItem("037000934677", 1.34, 1),
114             basketItem("030772076835", 3.07, 2),
115             basketItem("037000534358", 6.62, 1),
116             basketItem("037000808893", 5.64, 1)
117         )
118     }
119
120     private fun buildCouponsFromLocalDb(
121         tcbBaseUrl: String,
122         tcbAccessKey: String,
123         accessToken: String
124     ): MutableList<InputCoupon> {
125         val scannedCoupons = listOf(
126             "8112009988459000019133924009755364",
127             "8112009988459000039133772240739897",
128             "8112009988459000049133939957096441"
129         )
130
131         val resolvedCoupons = TcbScannedGs1Service.parseScannedGs1s(
132             tcbBaseUrl,
133             tcbAccessKey,
134             accessToken,
135             scannedCoupons
136         )
137
138         val mapper = ObjectMapper()
139         val coupons = mutableListOf<InputCoupon>()
140
141         Jedis("localhost", 6379).use { jedis ->
142             for (resolvedCoupon in resolvedCoupons) {
143                 val purchaseRequirementJson = jedis.get(resolvedCoupon.baseGs1) ?: continue
144
145                 val purchaseRequirement = mapper.readValue(
146                     purchaseRequirementJson,
147                     PurchaseRequirement::class.java
148                 )
149
150                 coupons.add(
151                     InputCoupon().apply {
152                         gs1 = resolvedCoupon.gs1
153                         this.purchaseRequirement = purchaseRequirement
154                         validated = resolvedCoupon.validated
155                     }
156                 )
157             }
158         }
159
160         return coupons
161     }
162
163     private fun basketItem(productCode: String, price: Double, quantity: Int): BasketItem {
164         return BasketItem().apply {
165             this.productCode = productCode
166             this.price = price
167             this.quantity = quantity
168             this.unit = "item"
169         }
170     }
171 }
```
