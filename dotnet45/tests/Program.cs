using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Threading.Tasks;
using TheCouponBureau.BasketValidation;

namespace TheCouponBureau.BasketValidation.SmokeTests
{
    internal static class Program
    {
        private const string AccessKey = "test-access-key";
        private const string CouponOne = "8112009988459000019133924009755364";
        private const string CouponTwo = "8112009988459000039133772240739897";

        private static int Main()
        {
            try
            {
                RunAsync().GetAwaiter().GetResult();
                Console.WriteLine("All native .NET Framework 4.5 DLL smoke tests passed.");
                return 0;
            }
            catch (Exception error)
            {
                Console.Error.WriteLine(error);
                return 1;
            }
        }

        private static async Task RunAsync()
        {
            using (var server = new MockTcbServer())
            using (var client = new BasketValidationClient())
            {
                string accessToken = await client.FetchAccessTokenAsync(
                    server.BaseUrl, AccessKey, "test-secret-key").ConfigureAwait(false);
                TestAssert.Equal("test-access-token", accessToken,
                    "Access token should be returned after a transient failure retry.");

                BasketValidationOutput validation = await client.ValidateBasketAsync(
                    server.BaseUrl, AccessKey, accessToken, CreateValidationRequest())
                    .ConfigureAwait(false);
                TestAssert.Equal(200L, validation.DiscountInCents,
                    "Both eligible coupons should be applied.");
                TestAssert.Equal(2, validation.AppliedCoupons.Count,
                    "Validation should return two applied coupons.");
                TestAssert.Equal(CouponOne, validation.AppliedCoupons[0].CouponCode,
                    "Applied coupons should preserve input order.");
                TestAssert.Equal(CouponTwo, validation.AppliedCoupons[1].CouponCode,
                    "Applied coupons should preserve input order.");

                List<SerializedGs1Data> resolved = await client.ParseScannedGs1sAsync(
                    server.BaseUrl, AccessKey, accessToken,
                    new List<string> { "8112009988459000" }, "retailer", null)
                    .ConfigureAwait(false);
                TestAssert.Equal(1, resolved.Count,
                    "A valid fetch code should resolve to one serialized coupon.");
                TestAssert.True(resolved[0].Validated,
                    "A fetch code resolved by TCB should be marked as validated.");
                TestAssert.Equal("811200998845900009", resolved[0].BaseGs1,
                    "The resolved coupon should include its base GS1.");

                RedeemResult redemption = await client.RedeemCouponsAsync(
                    server.BaseUrl, AccessKey, accessToken,
                    new List<string> { CouponOne }, "retailer", null)
                    .ConfigureAwait(false);
                TestAssert.Equal("success", redemption.Status,
                    "Redemption should return success.");
                TestAssert.Equal(1, redemption.TotalGs1sProcessed,
                    "Redemption should report one processed coupon.");
                Console.WriteLine("Redeem response:");
                Console.WriteLine(SerializeJson(redemption));

                Dictionary<string, string> rollback = await client.RollbackCouponsAsync(
                    server.BaseUrl, AccessKey, accessToken,
                    new List<string> { CouponOne }, "retailer").ConfigureAwait(false);
                TestAssert.True(rollback[CouponOne].Contains("\"status\":\"success\""),
                    "Rollback should return the TCB success response.");
                foreach (KeyValuePair<string, string> response in rollback)
                {
                    Console.WriteLine("Rollback response for " + response.Key + ":");
                    Console.WriteLine(response.Value);
                }

                AssertRequestHeaders(server.Requests);
                AssertRedemptionIdempotency(server.Requests);
            }
        }

        private static BasketValidationRequest CreateValidationRequest()
        {
            return new BasketValidationRequest
            {
                Basket = new List<BasketItem>
                {
                    new BasketItem
                    {
                        ProductCode = "037000930396", Price = 1.29,
                        Quantity = 1, Unit = "item"
                    },
                    new BasketItem
                    {
                        ProductCode = "030772076835", Price = 3.07,
                        Quantity = 1, Unit = "item"
                    }
                },
                Coupons = new List<string> { CouponOne, CouponTwo },
                Mode = "retailer"
            };
        }

        private static void AssertRequestHeaders(
            IEnumerable<MockTcbServer.RecordedRequest> requests)
        {
            foreach (MockTcbServer.RecordedRequest request in requests)
            {
                string apiKey;
                TestAssert.True(request.Headers.TryGetValue("x-api-key", out apiKey),
                    "Every TCB request should include x-api-key.");
                TestAssert.Equal(AccessKey, apiKey,
                    "Every TCB request should use the supplied access key.");
            }
        }

        private static void AssertRedemptionIdempotency(
            IEnumerable<MockTcbServer.RecordedRequest> requests)
        {
            MockTcbServer.RecordedRequest request = requests.FirstOrDefault(item =>
                item.Path == "/retailer/redeem" &&
                !item.Body.Contains("\"pre_process\":\"yes\""));
            TestAssert.True(request != null,
                "A final redemption request should be sent.");
            TestAssert.True(request.Body.Contains("\"client_txn_id\":"),
                "Final redemption should include an SDK-generated client_txn_id.");
        }

        private static string SerializeJson<T>(T value)
        {
            var serializer = new DataContractJsonSerializer(
                typeof(T),
                new DataContractJsonSerializerSettings
                {
                    UseSimpleDictionaryFormat = true
                });
            using (var stream = new MemoryStream())
            {
                serializer.WriteObject(stream, value);
                return Encoding.UTF8.GetString(stream.ToArray());
            }
        }
    }
}
