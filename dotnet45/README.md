# Native .NET Framework 4.5 SDK

See [Readme.DotNet45.md](Readme.DotNet45.md) for the complete integration guide.

`TcbBasketValidation.Client.dll` is a fully managed .NET Framework 4.5 SDK. It
calls TCB directly and performs basket validation inside the POS process. No
additional runtime or local offer database is required.

## Build

Build requirements are Node.js 18+ and .NET SDK 8+:

```bat
cd dotnet45
build-dll.cmd
```

The script transpiles and embeds the basket rule engine, embeds all managed
dependencies, and creates one deployable file:

```text
dotnet45\bin\Release\net45\TcbBasketValidation.Client.dll
```

Add the DLL as a reference to the .NET Framework 4.5 POS application.

### Cloud build

The `Build .NET Framework 4.5 DLL` GitHub Actions workflow builds and uploads
the DLL whenever the .NET SDK source changes. It can also be started manually
from the repository's **Actions** page. Published GitHub releases receive the
DLL as a release asset automatically.

## Use

```csharp
using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using TheCouponBureau.BasketValidation;

public async Task ValidateAndRedeemAsync()
{
    const string baseUrl = "https://api.try.thecouponbureau.org";
    const string accessKey = "YOUR_ACCESS_KEY";

    using (var client = new BasketValidationClient())
    {
        string accessToken = await client.FetchAccessTokenAsync(
            baseUrl, accessKey, "YOUR_SECRET_KEY");

        var input = new BasketValidationRequest
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

        BasketValidationOutput validation = await client.ValidateBasketAsync(
            baseUrl, accessKey, accessToken, input);

        var appliedGs1s = new List<string>();
        foreach (AppliedCoupon coupon in validation.AppliedCoupons)
            appliedGs1s.Add(coupon.CouponCode);

        // Apply validation.DiscountInCents and complete the transaction here.

        if (appliedGs1s.Count > 0)
        {
            RedeemResult redemption = await client.RedeemCouponsAsync(
                baseUrl, accessKey, accessToken, appliedGs1s, "retailer", null);
            Console.WriteLine(redemption.Status);
        }
    }
}
```

## Public methods

- `FetchAccessTokenAsync(...)`
- `ParseScannedGs1sAsync(...)`
- `ValidateBasketAsync(...)`
- `RedeemCouponsAsync(...)`
- `RollbackCouponsAsync(...)`

The SDK retries after 50, 100, and 200 milliseconds. Redemption uses batches
of 15, executes batches concurrently, and generates one stable `client_txn_id`
per batch. Coupon resolution preserves input order.

For accelerator mode, use `"accelerator"` and provide the retailer email
domain. API failures throw `TcbNativeException` with HTTP status and response.
