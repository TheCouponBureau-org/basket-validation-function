# Using `basket-validator-1.0-SNAPSHOT.jar`

This project builds the JAR:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
```

You can use it in 2 ways:

1. Run it through the CLI entrypoint
2. Call the validator classes from your own Java code

## 1. Build the JAR

From the `java/` folder:

```bash
./build-jar.sh
```

That creates:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
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

This example shows:

- one coupon with only `gs1` that can be resolved through TCB
- one coupon that already includes `base_gs1` and `purchase_requirement`

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
      "gs1": "8112109988459000269133587761214614",
      "base_gs1": "811210998845900026",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "037000618737",
          "037000758365"
        ],
        "second_purchase_gtins": [
          "7106919588011",
          "8952803493171",
          "1305192154937"
        ],
        "third_purchase_gtins": [
          "037000779681",
          "037000523505",
          "037000925033"
        ],
        "primary_purchase_save_value": 1,
        "primary_purchase_requirements": 6,
        "primary_purchase_req_code": 0,
        "additional_purchase_rules_code": 2,
        "second_purchase_requirements": 2,
        "second_purchase_req_code": 0,
        "third_purchase_requirements": 3,
        "third_purchase_req_code": 0,
        "save_value_code": 2,
        "applies_to_which_item": 0
      }
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
                      "gs1": "8112109988459000269133587761214614",
                      "base_gs1": "811210998845900026",
                      "purchase_requirement": {
                        "primary_purchase_gtins": [
                          "037000930396",
                          "037000934677",
                          "037000618737",
                          "037000758365"
                        ],
                        "second_purchase_gtins": [
                          "7106919588011",
                          "8952803493171",
                          "1305192154937"
                        ],
                        "third_purchase_gtins": [
                          "037000779681",
                          "037000523505",
                          "037000925033"
                        ],
                        "primary_purchase_save_value": 1,
                        "primary_purchase_requirements": 6,
                        "primary_purchase_req_code": 0,
                        "additional_purchase_rules_code": 2,
                        "second_purchase_requirements": 2,
                        "second_purchase_req_code": 0,
                        "third_purchase_requirements": 3,
                        "third_purchase_req_code": 0,
                        "save_value_code": 2,
                        "applies_to_which_item": 0
                      }
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

If a coupon only contains `gs1` and does not contain `base_gs1` or `purchase_requirement`, the validator can resolve it through TCB APIs.

Set these optional fields on `BasketValidationInput` before calling:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org";
input.tcbAccessKey = "YOUR_ACCESS_KEY";
input.tcbSecretKey = "YOUR_SECRET_KEY";
```

If these are not provided, coupons missing `purchase_requirement` are ignored.

Example:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org/";
input.tcbAccessKey = "8053fd0f80cf3778659def1359cac218";
input.tcbSecretKey = "eb42623aa2675e50f15da4f6d4aa0ad6";
```

## 6. CLI usage with JSON string

You can pass the full input JSON as the first argument.

Without TCB credentials:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT-all.jar '{"basket":[{"product_code":"037000758365","price":1.99,"quantity":12,"unit":"item"},{"product_code":"7106919588011","price":1.81,"quantity":2,"unit":"item"},{"product_code":"037000925033","price":1.59,"quantity":3,"unit":"item"}],"coupons":[{"gs1":"8112109988459000269133321426026193"}]}'
```

With TCB credentials:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT-all.jar '{"basket":[{"product_code":"037000758365","price":1.99,"quantity":12,"unit":"item"},{"product_code":"7106919588011","price":1.81,"quantity":2,"unit":"item"},{"product_code":"037000925033","price":1.59,"quantity":3,"unit":"item"}],"coupons":[{"gs1":"8112109988459000269133321426026193"},{"gs1":"8112109988459000269133587761214614","base_gs1":"811210998845900026","purchase_requirement":{"primary_purchase_gtins":["037000930396","037000934677","037000618737","037000758365"],"second_purchase_gtins":["7106919588011","8952803493171","1305192154937"],"third_purchase_gtins":["037000779681","037000523505","037000925033"],"primary_purchase_save_value":1,"primary_purchase_requirements":6,"primary_purchase_req_code":0,"additional_purchase_rules_code":2,"second_purchase_requirements":2,"second_purchase_req_code":0,"third_purchase_requirements":3,"third_purchase_req_code":0,"save_value_code":2,"applies_to_which_item":0}}]}' "https://api.try.thecouponbureau.org/" "8053fd0f80cf3778659def1359cac218" "eb42623aa2675e50f15da4f6d4aa0ad6"
```

## 7. Redeem coupons in TCB after discount application

After your retailer system applies the discount, it should redeem the applied coupons in TCB.

Use:

- `org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService.redeemCoupons(...)`

This method:

- accepts an array/list of GS1 coupon codes
- gets or reuses the cached TCB access token
- calls the same `retailer/redeem` API
- returns the raw JSON response body from TCB
- does not send the `pre_process` field

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

## 8. Important dependency note

For direct `java -jar` usage, prefer:

```bash
target/basket-validator-1.0-SNAPSHOT-all.jar
```

That fat JAR already includes dependencies.
