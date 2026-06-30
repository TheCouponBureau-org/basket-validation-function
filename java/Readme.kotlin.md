# Using `basket-validator-1.0-SNAPSHOT.jar` from Kotlin

This project builds:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
```

You can use this JAR from a Kotlin project in 2 common ways:

1. Call the validator classes directly from Kotlin
2. Run the validator as a CLI from your Kotlin app or scripts

## 1. Build the JAR

From the `java/` folder:

```bash
./build-jar.sh
```

Output:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
```

## 2. Add the JAR to your Kotlin project

If you are using a local/manual setup, copy the JAR into your Kotlin project:

```bash
your-kotlin-project/lib/basket-validator-1.0-SNAPSHOT.jar
```

## 3. Kotlin compile/run with classpath

Example using `kotlinc`:

```bash
kotlinc -cp "lib/basket-validator-1.0-SNAPSHOT.jar" src/main/kotlin/Main.kt -d app.jar
java -cp "app.jar:lib/basket-validator-1.0-SNAPSHOT.jar" MainKt
```

On Windows, use `;` instead of `:`.

## 4. Call the validator directly from Kotlin

Main classes:

- `org.thecouponbureau.validate.basket.core.BasketValidator`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon`
- `org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement`

Example:

```kotlin
import org.thecouponbureau.validate.basket.core.BasketValidator
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement

fun main() {
    val item1 = BasketItem().apply {
        productCode = "037000930396"
        price = 1.29
        quantity = 1
        unit = "item"
    }

    val item2 = BasketItem().apply {
        productCode = "037000934677"
        price = 1.34
        quantity = 1
        unit = "item"
    }

    val requirement = PurchaseRequirement().apply {
        primaryPurchaseGtins = listOf("037000930396", "037000934677")
        primaryPurchaseRequirements = 2L
        primaryPurchaseReqCode = 0
        primaryPurchaseSaveValue = 100L
        saveValueCode = 0
    }

    val coupon = Coupon().apply {
        gs1 = "8112009988459000019133924009755364"
        baseGs1 = "811200998845900001"
        purchaseRequirement = requirement
    }

    val input = BasketValidationInput().apply {
        basket = mutableListOf(item1, item2)
        coupons = mutableListOf(coupon)
    }

    val result = BasketValidator.validateBasketHelper(input)
    println(result.basketValidationOutput.discountInCents)
}
```

## 5. JSON-driven usage from Kotlin

The Java models expect `snake_case` JSON when using Jackson.

Example JSON:

```json
{
  "basket": [
    {
      "product_code": "037000930396",
      "price": 1.29,
      "quantity": 1,
      "unit": "item"
    }
  ],
  "coupons": [
    {
      "gs1": "8112009988459000019133924009755364",
      "base_gs1": "811200998845900001",
      "purchase_requirement": {
        "primary_purchase_gtins": ["037000930396"],
        "primary_purchase_requirements": 1,
        "primary_purchase_req_code": 0,
        "primary_purchase_save_value": 100,
        "save_value_code": 0
      }
    }
  ]
}
```

Kotlin example:

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import java.io.File
import org.thecouponbureau.validate.basket.core.BasketValidator
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput

fun main() {
    val mapper = ObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    }

    val input = mapper.readValue(
        File("input.json"),
        BasketValidationInput::class.java
    )

    val result = BasketValidator.validateBasketHelper(input)
    println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
}
```

## 6. GS1-only coupon resolution from Kotlin

If your coupon only contains `gs1` and does not include `base_gs1` or `purchase_requirement`, you can let the validator resolve it through TCB APIs.

Set these optional fields before calling:

```kotlin
input.tcbBaseUrl = "https://api.try.thecouponbureau.org"
input.tcbAccessKey = "YOUR_ACCESS_KEY"
input.tcbSecretKey = "YOUR_SECRET_KEY"
```

If these are not set, coupons missing `purchase_requirement` are ignored.

## 7. Running the CLI from Kotlin or shell

The project includes:

```bash
./run-validation.sh
```

Examples:

```bash
./run-validation.sh input-gs1-only.json
```

```bash
./run-validation.sh input-gs1-only.json "https://api.try.thecouponbureau.org" "YOUR_ACCESS_KEY" "YOUR_SECRET_KEY"
```

You can also invoke that script from Kotlin using `ProcessBuilder` if you prefer CLI integration over direct library usage.

## 8. Dependency note

This JAR is not a fat JAR. Your Kotlin project still needs runtime dependencies available, especially:

- Jackson
- Apache POI
- Log4j

If you want a single self-contained artifact, build an uber JAR separately.
