# Using `basket-validator-1.0-SNAPSHOT.jar`

Use this package as a library from your Java code.

## 1. Build the JAR

From the `java/` folder:

```bash
./build-jar.sh
```

That creates the regular JAR and the fat JAR:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
target/basket-validator-1.0-SNAPSHOT-all.jar
```

## 2. Add the JAR to another Java project

If your project is not using Maven publishing, copy the fat JAR into your project, for example:

```bash
your-project/lib/basket-validator-1.0-SNAPSHOT-all.jar
```

Then add it to your classpath when compiling and running.

Example:

```bash
javac -cp "lib/basket-validator-1.0-SNAPSHOT-all.jar" src/com/example/Main.java
java -cp "lib/basket-validator-1.0-SNAPSHOT-all.jar:src" com.example.Main
```

On Windows use `;` instead of `:` in the classpath.

## 3. Use it from Java code

Main classes:

- `org.thecouponbureau.validate.basket.core.BasketValidator`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult`
- `org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService`
- `org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService`

Example:

```java
import java.util.ArrayList;
import java.util.List;

import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

public class Main {
    public static void main(String[] args) {
        BasketItem item1 = new BasketItem();
        item1.productCode = "037000930396";
        item1.price = 1.29;
        item1.quantity = 1;
        item1.unit = "item";

        BasketItem item2 = new BasketItem();
        item2.productCode = "037000934677";
        item2.price = 1.34;
        item2.quantity = 1;
        item2.unit = "item";

        PurchaseRequirement requirement = new PurchaseRequirement();
        requirement.primaryPurchaseGtins = List.of("037000930396", "037000934677");
        requirement.primaryPurchaseRequirements = 2L;
        requirement.primaryPurchaseReqCode = 0;
        requirement.primaryPurchaseSaveValue = 100L;
        requirement.saveValueCode = 0;

        Coupon coupon = new Coupon();
        coupon.gs1 = "8112009988459000019133924009755364";
        coupon.baseGs1 = "811200998845900001";
        coupon.purchaseRequirement = requirement;

        BasketValidationInput input = new BasketValidationInput();
        input.basket = new ArrayList<>();
        input.basket.add(item1);
        input.basket.add(item2);
        input.coupons = new ArrayList<>();
        input.coupons.add(coupon);

        ValidationResult result = BasketValidator.validateBasketHelper(input);

        System.out.println(result.basketValidationOutput.discountInCents);
    }
}
```

## 4. JSON-string driven usage inside your own code

If your application already works with JSON strings, deserialize into `BasketValidationInput`.

The project uses Jackson `SNAKE_CASE`, so JSON like this maps correctly.

This example shows the supported caller input shape:

- each coupon object contains only `gs1`
- `base_gs1` and `purchase_requirement` are internal fields populated by the SDK after TCB resolution and should not be supplied by the caller

```json
{
  "basket": [
    {
      "product_code": "037000758365",
      "price": 1.99,
      "quantity": 12,
      "unit": "item"
    },
    {
      "product_code": "7106919588011",
      "price": 1.81,
      "quantity": 2,
      "unit": "item"
    },
    {
      "product_code": "037000925033",
      "price": 1.59,
      "quantity": 3,
      "unit": "item"
    }
  ],
  "coupons": [
    {
      "gs1": "8112109988459000269133321426026193"
    },
    {
      "gs1": "8112109988459000269133587761214614"
    }
  ]
}
```

Example:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

public class Main {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        String jsonInput = """
                {
                  "basket": [
                    {
                      "product_code": "037000758365",
                      "price": 1.99,
                      "quantity": 12,
                      "unit": "item"
                    },
                    {
                      "product_code": "7106919588011",
                      "price": 1.81,
                      "quantity": 2,
                      "unit": "item"
                    },
                    {
                      "product_code": "037000925033",
                      "price": 1.59,
                      "quantity": 3,
                      "unit": "item"
                    }
                  ],
                  "coupons": [
                    {
                      "gs1": "8112109988459000269133321426026193"
                    },
                    {
                      "gs1": "8112109988459000269133587761214614"
                    }
                  ]
                }
                """;

        BasketValidationInput input =
                mapper.readValue(jsonInput, BasketValidationInput.class);

        ValidationResult result = BasketValidator.validateBasketHelper(input);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
```

## 5. If you want GS1-only coupon resolution

The caller should send coupons with only `gs1`. The validator resolves `base_gs1` and `purchase_requirement` internally through TCB APIs.

Set these optional fields on `BasketValidationInput` before calling:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org";
input.tcbAccessKey = "YOUR_ACCESS_KEY";
input.tcbSecretKey = "YOUR_SECRET_KEY";
```

If these are not provided, unresolved coupons are ignored because the SDK cannot fetch `purchase_requirement`.

Example:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org/";
input.tcbAccessKey = "8053fd0f80cf3778659def1359cac218";
input.tcbSecretKey = "eb42623aa2675e50f15da4f6d4aa0ad6";
```

Optional debug logging:

```java
input.enableLogging = true;
```

When `enableLogging` is `true`, the validator prints pretty JSON logs for:

- the input payload before validation starts
- each TCB resolution redeem request payload used to fetch missing `purchase_requirement`
- each TCB resolution redeem response body returned by the API
- the resolved coupon JSON after `purchase_requirement` and `base_gs1` are populated

The resolved output log also prints `coupon_gs1_order` so you can verify that coupon order is still maintained based on the input `gs1` values.

The input log redacts `tcbAccessKey` and `tcbSecretKey`.

## 6. Redeem coupons in TCB after discount application

After your retailer system applies the discount, it should redeem the applied coupons in TCB.

Use:

- `org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService.redeemCoupons(...)`

This method:

- accepts an array/list of GS1 coupon codes
- gets or reuses the cached TCB access token
- calls the same `retailer/redeem` API
- if more than `15` GS1s are provided, splits them into chunks of `15`
- sends those redeem calls in parallel for faster network performance
- merges the chunk responses into one JSON response
- returns the raw JSON response body from TCB
- does not send the `pre_process` field
- generates one `client_txn_id` per chunked redemption request
- reuses that same `client_txn_id` across retries for idempotency

Example:

```java
import java.util.List;

import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService;

public class RedeemExample {
    public static void main(String[] args) {
        String responseJson = TcbCouponRedeemService.redeemCoupons(
                "https://api.try.thecouponbureau.org/",
                "8053fd0f80cf3778659def1359cac218",
                "eb42623aa2675e50f15da4f6d4aa0ad6",
                List.of(
                        "8112109988459000269133321426026193",
                        "8112109988459000269133587761214614"
                )
        );

        System.out.println(responseJson);
    }
}
```

Note: `enableLogging` only affects validation-time GS1 resolution inside `BasketValidator.validateBasketHelper(...)`. It does not change the output of `TcbCouponRedeemService.redeemCoupons(...)`.

## 7. Dependency note

For application integration, use:

```bash
target/basket-validator-1.0-SNAPSHOT-all.jar
```

That fat JAR already includes dependencies for embedding in your Java project.

## 8. Rollback redeemed coupons in TCB

If your retailer needs to reverse previously redeemed coupons, use:

- `org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService.rollbackCoupons(...)`

This method:

- accepts a list of GS1 coupon codes
- gets or reuses the cached TCB access token
- calls `DELETE /retailer/rollback/{gs1}`
- calls each rollback in parallel, one API request per GS1
- returns a `Map<String, String>` where:
  - key = GS1
  - value = raw JSON response from TCB

Example:

```java
import java.util.List;
import java.util.Map;

import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService;

public class RollbackExample {
    public static void main(String[] args) {
        Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
                "https://api.try.thecouponbureau.org/",
                "8053fd0f80cf3778659def1359cac218",
                "eb42623aa2675e50f15da4f6d4aa0ad6",
                List.of(
                        "8112109988459000269133321426026193",
                        "8112109988459000269133587761214614"
                )
        );

        for (Map.Entry<String, String> entry : rollbackResponses.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }
}
```
