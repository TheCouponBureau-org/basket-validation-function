# Java Integration

Send the basket and scanned coupon codes to `validateBasketHelper(...)`;
the SDK obtains coupon purchase requirements and calculates the discount in
your application. Internet access to TCB is required.

For the other language, see [Kotlin](Readme.kotlin.md).

## 1. Build and add the SDK

Use JDK 17+ and Maven. From `java/`, run:

```bash
./build-jar.sh
```

Add `target/basket-validator-1.0-SNAPSHOT.jar` to your application's classpath,
along with its runtime dependencies from [pom.xml](pom.xml). This is a standard
JAR; dependencies are not bundled. Kotlin uses the same Java SDK.

## 2. Validate a basket

The example uses the TCB test environment. Replace the credentials and scanned
code with values issued for your environment. Keep credentials in application
configuration. Basket prices are per-unit currency amounts; discounts are cents.

```java
import java.util.List;
import java.util.stream.Collectors;
import org.thecouponbureau.validate.basket.Services.TcbTokenService;
import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService;
import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.*;

// Inside your checkout method:
String baseUrl = "https://api.try.thecouponbureau.org";
String accessKey = "YOUR_ACCESS_KEY";
String secretKey = "YOUR_SECRET_KEY";
String accessToken = TcbTokenService.fetchAccessToken(baseUrl, accessKey, secretKey);

BasketItem item = new BasketItem();
item.productCode = "037000930396";
item.price = 1.29;
item.quantity = 2;
item.unit = "item";

InputCoupon coupon = new InputCoupon();
coupon.gs1 = "8112009988459000019133924009755364";
// Leave purchaseRequirement and validated unset: TCB supplies the offer data.

BasketValidationInput input = new BasketValidationInput();
input.basket = List.of(item);
input.coupons = List.of(coupon);
input.tcbBaseUrl = baseUrl;
input.tcbAccessKey = accessKey;
input.tcbAccessToken = accessToken;
input.mode = "retailer";

ValidationResult result = BasketValidator.validateBasketHelper(input);
if (result.error != null) {
    throw new IllegalStateException(result.error.code + ": " + result.error.message);
}
BasketValidationOutput output = result.basketValidationOutput;
System.out.println("Discount in cents: " + output.discountInCents);
List<String> appliedGs1s = output.appliedCoupons.stream()
        .map(applied -> applied.couponCode)
        .collect(Collectors.toList());
```

Add one `InputCoupon` per scanned value. Fetch codes can also be supplied in
`gs1`; the helper resolves them through TCB. No separate parsing call is needed.

The helper obtains purchase requirements and evaluates qualifying basket items.
Validation does **not** finalize redemption. `output.appliedCoupons` contains
the serialized coupon codes to pass to `redeemCoupons(...)` after checkout.

Handle `result.error` and API exceptions before applying a discount. An empty
`appliedCoupons` list means no coupon discount should be applied; sample codes
do not guarantee a discount against live test data.

### Sample input and output

The same input in JSON (using Jackson's `SNAKE_CASE` naming strategy):

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

No `purchase_requirement` is supplied; the SDK obtains it from TCB.
Assuming TCB accepts the coupon and returns an offer for $1 off two qualifying
items, the serialized validation result is:

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

This is the basket validation output, not the final redemption response.

## 3. Complete checkout and redeem

Use `output.discountInCents` as the POS coupon discount. After the transaction
succeeds, redeem only the applied coupon codes:

```java
if (!appliedGs1s.isEmpty()) {
    String redeemResponse = TcbCouponRedeemService.redeemCoupons(
            baseUrl, accessKey, accessToken, appliedGs1s,
            input.mode, input.retailerEmailDomain);
    // Inspect and save redeemResponse with the POS transaction.
}
```

Check the redemption response, including `status`, `status_code`, and
`newly_redeemed`, before treating all coupons as redeemed. Handle partial
redemption or API failures in your checkout reconciliation flow.

## 4. Roll back only when a redeemed transaction is voided

Run this separately in the void/cancellation handler, using the coupon codes
confirmed redeemed for that transaction (saved as `redeemedGs1s`), its mode,
and valid credentials:

```java
java.util.Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
        baseUrl, accessKey, accessToken, redeemedGs1s, input.mode);
```

Check each rollback response. Do not call rollback during successful checkout.

## Retailer or accelerator mode

The example defaults to retailer mode. For accelerator integration, set these
fields **before validation**:

```java
input.mode = "accelerator";
input.retailerEmailDomain = "retailer.example";
```

Null, blank, or `"retailer"` mode selects retailer integration. `"accelerator"`
selects accelerator integration and requires a nonblank retailer email domain. Modes are
case-insensitive; unsupported values are rejected. For JSON input the domain
field is `retailer_email_domain`.

Pass the same mode/domain to final redemption as shown above. Rollback takes
only the mode; no retailer email domain is needed.
