# Java integration flow

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

Use the JAR for integration:

```bash
target/basket-validator-1.0-SNAPSHOT.jar
```

## 2. Add the JAR to your project

Copy the fat JAR into your application, for example:

```bash
your-project/lib/basket-validator-1.0-SNAPSHOT.jar
```

After that, add the JAR from your `lib/` folder to your Java project classpath using your normal build setup.

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

#### Step 2. Get the TCB token

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

#### Step 3. Resolve scanned values into serialized coupons and `base_gs1`

Request:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service;

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

ObjectMapper mapper = new ObjectMapper();
mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

System.out.println("Resolved scanned GS1 response:");
System.out.println(
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resolved));

for (TcbScannedGs1Service.SerializedGs1Data item : resolved) {
    System.out.println(
            "serialized_gs1=" + item.gs1
                    + ", base_gs1=" + item.baseGs1
                    + ", validated=" + item.validated);
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

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;

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

```java
import java.util.ArrayList;
import java.util.List;

import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

List<InputCoupon> locallyEligibleCoupons = new ArrayList<>();

for (InputCoupon coupon : coupons) {
    LocalBasketValidationInput localInput = new LocalBasketValidationInput();
    localInput.basket = basket;
    localInput.coupons = List.of(coupon);

    ValidationResult localResult = BasketValidator.localBasketValidation(localInput);

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
- if `validated` is not `true`, `validateBasketHelper(...)` calls TCB `retailer/redeem` with:
  - `pre_process = "yes"`
  - `no_purchase_requirement = "yes"`
- coupons not returned in `newly_redeemed` are removed
- the remaining coupons already have local `purchase_requirement` objects, so the final discount is calculated locally

Request:

```java
import java.util.ArrayList;
import java.util.List;

import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
List<BasketItem> basket = new ArrayList<>();

BasketItem item1 = new BasketItem();
item1.productCode = "037000930396";
item1.price = 1.29;
item1.quantity = 1;
item1.unit = "item";
basket.add(item1);

BasketItem item2 = new BasketItem();
item2.productCode = "037000934677";
item2.price = 1.34;
item2.quantity = 1;
item2.unit = "item";
basket.add(item2);

BasketItem item3 = new BasketItem();
item3.productCode = "030772076835";
item3.price = 3.07;
item3.quantity = 2;
item3.unit = "item";
basket.add(item3);

BasketItem item4 = new BasketItem();
item4.productCode = "037000534358";
item4.price = 6.62;
item4.quantity = 1;
item4.unit = "item";
basket.add(item4);

BasketItem item5 = new BasketItem();
item5.productCode = "037000808893";
item5.price = 5.64;
item5.quantity = 1;
item5.unit = "item";
basket.add(item5);

List<InputCoupon> coupons = new ArrayList<>();
for (InputCoupon localCoupon : locallyEligibleCoupons) {
    InputCoupon coupon = new InputCoupon();
    coupon.gs1 = localCoupon.gs1;
    coupon.purchaseRequirement = localCoupon.purchaseRequirement;
    coupon.validated = localCoupon.validated;
    coupons.add(coupon);
}

BasketValidationInput input = new BasketValidationInput();
input.basket = basket;
input.coupons = coupons;
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

#### Step 8. Call `validateBasketHelper(...)`

Request:

```java
input.tcbBaseUrl = "https://api.try.thecouponbureau.org";
input.tcbAccessKey = "YOUR_ACCESS_KEY";
input.tcbAccessToken = accessToken;

ValidationResult result = BasketValidator.validateBasketHelper(input);
```

What happens inside this second validation pass:

1. Coupons with `validated = true` are kept as already validated.
2. Coupons without `validated = true` are sent to TCB `retailer/redeem`.
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

```java
String redeemResponseJson =
        org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService.redeemCoupons(
                "https://api.try.thecouponbureau.org",
                "YOUR_ACCESS_KEY",
                accessToken,
                Arrays.asList(
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

#### Step 10. Roll back redeemed coupons if the transaction is voided

Request:

```java
Map<String, String> rollbackResponses =
        org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService.rollbackCoupons(
                "https://api.try.thecouponbureau.org",
                "YOUR_ACCESS_KEY",
                accessToken,
                Arrays.asList(
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

## Complete Java Example

The following example hardcodes basket data and coupon purchase requirements directly in code and shows the full SDK flow without using any fetch code.

```java
01 package demo;
02 
03 import java.util.ArrayList;
04 import java.util.Arrays;
05 import java.util.List;
06 import java.util.Map;
07 import java.util.stream.Collectors;
08 
09 import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService;
10 import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService;
11 import org.thecouponbureau.validate.basket.Services.TcbTokenService;
12 import org.thecouponbureau.validate.basket.core.BasketValidator;
13 import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon;
14 import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
15 import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
16 import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
17 import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput;
18 import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;
19 import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;
20 
21 public class EndToEndBasketValidationExample {
22 
23     public static void main(String[] args) throws Exception {
24         String tcbBaseUrl = "https://api.try.thecouponbureau.org";
25         String tcbAccessKey = "YOUR_ACCESS_KEY";
26         String tcbSecretKey = "YOUR_SECRET_KEY";
27 
28         String accessToken = TcbTokenService.fetchAccessToken(
29                 tcbBaseUrl,
30                 tcbAccessKey,
31                 tcbSecretKey);
32 
33         List<BasketItem> basket = buildBasket();
34         List<InputCoupon> couponsFromLocalDb = buildCouponsFromLocalDb();
35 
36         List<InputCoupon> locallyEligibleCoupons = new ArrayList<>();
37 
38         for (InputCoupon coupon : couponsFromLocalDb) {
39             LocalBasketValidationInput localInput = new LocalBasketValidationInput();
40             localInput.basket = basket;
41             localInput.coupons = List.of(coupon);
42 
43             ValidationResult localResult = BasketValidator.localBasketValidation(localInput);
44 
45             if (localResult.error != null) {
46                 continue;
47             }
48 
49             if (localResult.basketValidationOutput != null
50                     && localResult.basketValidationOutput.discountInCents > 0) {
51                 locallyEligibleCoupons.add(coupon);
52             }
53         }
54 
55         BasketValidationInput validateInput = new BasketValidationInput();
56         validateInput.basket = basket;
57         validateInput.coupons = locallyEligibleCoupons;
58         validateInput.tcbBaseUrl = tcbBaseUrl;
59         validateInput.tcbAccessKey = tcbAccessKey;
60         validateInput.tcbAccessToken = accessToken;
61         validateInput.enableLogging = true;
62 
63         ValidationResult finalResult =
64                 BasketValidator.validateBasketHelper(validateInput);
65 
66         System.out.println("discount_in_cents = "
67                 + finalResult.basketValidationOutput.discountInCents);
68 
69         for (AppliedCoupon appliedCoupon : finalResult.basketValidationOutput.appliedCoupons) {
70             System.out.println("coupon_code = " + appliedCoupon.couponCode);
71             System.out.println("face_value_in_cents = " + appliedCoupon.faceValueInCents);
72             System.out.println("gtins = " + appliedCoupon.productCodes.get("gtins"));
73         }
74 
75         List<String> appliedCouponGs1s =
76                 finalResult.basketValidationOutput.appliedCoupons.stream()
77                         .map(appliedCoupon -> appliedCoupon.couponCode)
78                         .collect(Collectors.toList());
79 
80         // Transaction done in POS using finalResult.basketValidationOutput.discountInCents
81         // Only after transaction success should retailer redeem the applied coupons in TCB.
82 
83         String redeemResponse = TcbCouponRedeemService.redeemCoupons(
84                 tcbBaseUrl,
85                 tcbAccessKey,
86                 accessToken,
87                 appliedCouponGs1s);
88 
89         System.out.println("redeemResponse = " + redeemResponse);
90 
91         // If transaction is voided later, roll back those redeemed coupons.
92         Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
93                 tcbBaseUrl,
94                 tcbAccessKey,
95                 accessToken,
96                 appliedCouponGs1s);
97 
98         System.out.println("rollbackResponses = " + rollbackResponses);
99     }
100 
101     private static List<BasketItem> buildBasket() {
102         List<BasketItem> basket = new ArrayList<>();
103 
104         basket.add(basketItem("037000930396", 1.29, 1));
105         basket.add(basketItem("037000934677", 1.34, 1));
106         basket.add(basketItem("030772076835", 3.07, 2));
107         basket.add(basketItem("037000534358", 6.62, 1));
108         basket.add(basketItem("037000808893", 5.64, 1));
109 
110         return basket;
111     }
112 
113     private static List<InputCoupon> buildCouponsFromLocalDb() {
114         List<InputCoupon> coupons = new ArrayList<>();
115 
116         InputCoupon couponOne = new InputCoupon();
117         couponOne.gs1 = "8112009988459000019133924009755364";
118         couponOne.purchaseRequirement = purchaseRequirement(
119                 Arrays.asList("037000930396", "037000934677", "012345678912"),
120                 Arrays.asList("7106919588011", "8952803493171", "5012345678900"),
121                 100L,
122                 2L,
123                 0,
124                 0);
125         coupons.add(couponOne);
126 
127         InputCoupon couponTwo = new InputCoupon();
128         couponTwo.gs1 = "8112009988459000039133772240739897";
129         couponTwo.purchaseRequirement = purchaseRequirement(
130                 Arrays.asList("037000761648", "037000925323"),
131                 Arrays.asList("030772076835", "030772076880"),
132                 100L,
133                 2L,
134                 0,
135                 0);
136         coupons.add(couponTwo);
137 
138         InputCoupon couponThree = new InputCoupon();
139         couponThree.gs1 = "8112009988459000049133939957096441";
140         couponThree.purchaseRequirement = purchaseRequirement(
141                 Arrays.asList("037000523550", "037000758365"),
142                 Arrays.asList("030772118054", "030772118092"),
143                 100L,
144                 2L,
145                 0,
146                 0);
147         coupons.add(couponThree);
148 
149         return coupons;
150     }
151 
152     private static PurchaseRequirement purchaseRequirement(
153             List<String> primaryPurchaseGtins,
154             List<String> primaryPurchaseEans,
155             Long primaryPurchaseSaveValue,
156             Long primaryPurchaseRequirements,
157             Integer primaryPurchaseReqCode,
158             Integer saveValueCode) {
159 
160         PurchaseRequirement purchaseRequirement = new PurchaseRequirement();
161         purchaseRequirement.primaryPurchaseGtins = primaryPurchaseGtins;
162         purchaseRequirement.primaryPurchaseEans = primaryPurchaseEans;
163         purchaseRequirement.primaryPurchaseSaveValue = primaryPurchaseSaveValue;
164         purchaseRequirement.primaryPurchaseRequirements = primaryPurchaseRequirements;
165         purchaseRequirement.primaryPurchaseReqCode = primaryPurchaseReqCode;
166         purchaseRequirement.saveValueCode = saveValueCode;
167         return purchaseRequirement;
168     }
169 
170     private static BasketItem basketItem(String productCode, double price, int quantity) {
171         BasketItem item = new BasketItem();
172         item.productCode = productCode;
173         item.price = price;
174         item.quantity = quantity;
175         item.unit = "item";
176         return item;
177     }
178 }
```
