# TCB Basket Validation SDK for .NET Framework 4.5

`TcbBasketValidation.Client.dll` is a native, fully managed .NET Framework 4.5 SDK. It connects directly to TCB and performs basket validation inside the POS application.

- No Java installation is required.
- No Docker container or separate API service is required.
- No Redis or local offer database is required.
- TCB purchase requirements are resolved automatically during validation.

## 1. Build the DLL

Build requirements:

- Node.js 18 or later
- .NET SDK 8 or later

From a Windows command prompt:

```bat
cd dotnet45
build-dll.cmd
```

The deployable DLL is created at:

```text
dotnet45\bin\Release\net45\TcbBasketValidation.Client.dll
```

The basket rule engine and its managed dependencies are embedded in this DLL.

### Build in GitHub Actions

The repository includes `.github/workflows/build-dotnet45-dll.yml`. The workflow:

1. Builds the DLL on a Windows GitHub-hosted runner.
2. Runs network-independent .NET Framework 4.5 smoke tests against the compiled DLL.
3. Uploads `TcbBasketValidation.Client.dll` only after the tests pass.
4. Attaches the DLL to a GitHub release when a release is published.

Run it manually from **GitHub > Actions > Build .NET Framework 4.5 DLL > Run workflow**, or let it run automatically when relevant SDK files change.

## 2. Add the DLL to the POS project

1. Copy `TcbBasketValidation.Client.dll` into the application's `lib` folder.
2. In Visual Studio, right-click **References** and select **Add Reference**.
3. Browse to the DLL and add it to the project.
4. Confirm that **Copy Local** is enabled for the reference.

Import the SDK namespace:

```csharp
using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using TheCouponBureau.BasketValidation;
```

## 3. Get an access token

Fetch the token using the TCB endpoint, access key, and secret key:

```csharp
string accessToken;

using (var client = new BasketValidationClient())
{
    accessToken = await client.FetchAccessTokenAsync(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        "YOUR_SECRET_KEY");
}
```

`FetchAccessTokenAsync` returns the `x-access-token` value as a string. Store it securely and reuse it until it approaches its 24-hour expiration.

## 4. Validate the basket

Pass basket items and scanned coupon codes. The SDK validates coupons with TCB, retrieves their purchase requirements, removes coupons that TCB does not return as valid, and calculates the applicable discount.

```csharp
BasketValidationOutput validation;

using (var client = new BasketValidationClient())
{
    var request = new BasketValidationRequest
    {
        Basket = new List<BasketItem>
        {
            new BasketItem
            {
                ProductCode = "037000930396",
                Price = 1.29,
                Quantity = 2,
                Unit = "item"
            },
            new BasketItem
            {
                ProductCode = "037000934677",
                Price = 1.34,
                Quantity = 2,
                Unit = "item"
            }
        },
        Coupons = new List<string>
        {
            "8112009988459000019133924009755364",
            "8112209988459000350001"
        },
        Mode = "retailer"
    };

    validation = await client.ValidateBasketAsync(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        request);
}
```

Example response object serialized as JSON:

```json
{
  "discount_in_cents": 263,
  "applied_coupons": [
    {
      "coupon_code": "8112009988459000019133924009755364",
      "face_value_in_cents": 263,
      "product_codes": {
        "gtins": [
          "037000930396",
          "037000934677"
        ]
      }
    }
  ]
}
```

Apply `DiscountInCents` to the transaction. Use only the coupon codes returned in `AppliedCoupons` for final redemption.

## 5. Redeem applied coupons

Call redemption only after the transaction and discount have completed successfully:

```csharp
var appliedGs1s = new List<string>();

foreach (AppliedCoupon coupon in validation.AppliedCoupons)
{
    appliedGs1s.Add(coupon.CouponCode);
}

RedeemResult redemption;

using (var client = new BasketValidationClient())
{
    redemption = await client.RedeemCouponsAsync(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        appliedGs1s,
        "retailer",
        null);
}
```

Example response:

```json
{
  "status": "success",
  "status_code": "FULL_REDEMPTION",
  "newly_redeemed": [
    {
      "gs1": "8112009988459000019133924009755364",
      "master_offer_file": "811200998845900001"
    }
  ],
  "total_gs1s_processed": 1,
  "message": "Redeemed 1 gs1(s)"
}
```

The SDK splits more than 15 coupons into batches, executes those batches concurrently, and creates a stable `client_txn_id` for each batch.

## 6. Roll back coupons

If the transaction fails after redemption, roll back the affected coupons:

```csharp
Dictionary<string, string> rollbackResponses;

using (var client = new BasketValidationClient())
{
    rollbackResponses = await client.RollbackCouponsAsync(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        appliedGs1s,
        "retailer");
}
```

The dictionary key is the submitted GS1 and its value is the JSON response returned by TCB. Rollback requests execute concurrently.

## 7. Resolve scanned GS1 values separately

This optional method resolves scanned values into serialized GS1 and `base_gs1` values:

```csharp
List<SerializedGs1Data> resolved;

using (var client = new BasketValidationClient())
{
    resolved = await client.ParseScannedGs1sAsync(
        "https://api.try.thecouponbureau.org",
        "YOUR_ACCESS_KEY",
        accessToken,
        new List<string>
        {
            "8112009988459000019133924009755364",
            "8112009988459000"
        },
        "retailer",
        null);
}

foreach (SerializedGs1Data coupon in resolved)
{
    Console.WriteLine(
        "GS1: {0}, Base GS1: {1}, Validated: {2}",
        coupon.Gs1,
        coupon.BaseGs1,
        coupon.Validated);
}
```

Example response containing a locally parsed coupon and a fetch-code coupon resolved by TCB:

```json
[
  {
    "gs1": "8112009988459000019133924009755364",
    "base_gs1": "811200998845900001",
    "validated": false
  },
  {
    "gs1": "8112009988459000359133123456789012",
    "base_gs1": "811200998845900035",
    "validated": true
  }
]
```

A locally parsed serialized GS1 returns `validated: false`. A fetch code resolved successfully through TCB returns `validated: true`.

## 8. Accelerator mode

Use `"accelerator"` instead of `"retailer"` and provide the retailer email domain:

```csharp
request.Mode = "accelerator";
request.RetailerEmailDomain = "retailer.example.com";
```

Pass the same retailer email domain to `ParseScannedGs1sAsync` and `RedeemCouponsAsync`. Rollback does not require the retailer email domain.

## 9. Error handling

TCB HTTP failures throw `TcbNativeException`:

```csharp
try
{
    BasketValidationOutput result = await client.ValidateBasketAsync(
        baseUrl,
        accessKey,
        accessToken,
        request);
}
catch (TcbNativeException exception)
{
    Console.WriteLine("HTTP status: " + exception.StatusCode);
    Console.WriteLine("TCB response: " + exception.ResponseBody);
}
catch (ArgumentException exception)
{
    Console.WriteLine("Invalid SDK input: " + exception.Message);
}
```

TCB calls automatically retry after 50, 100, and 200 milliseconds. If all attempts fail, the final error is returned to the application.

## Recommended transaction flow

1. Fetch and securely cache the TCB access token.
2. Build the basket and scanned coupon list.
3. Call `ValidateBasketAsync`.
4. Apply the returned discount to the transaction.
5. Complete payment.
6. Call `RedeemCouponsAsync` for applied coupons.
7. Call `RollbackCouponsAsync` if redemption must be reversed.
