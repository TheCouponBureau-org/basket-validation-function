using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Threading.Tasks;

namespace TheCouponBureau.BasketValidation
{
    /// <summary>Fully managed .NET Framework 4.5 basket-validation SDK.</summary>
    public sealed class BasketValidationClient : IDisposable
    {
        private const int ChunkSize = 15;
        private readonly TcbApiClient api = new TcbApiClient();
        private readonly BasketRuleEngine ruleEngine = new BasketRuleEngine();

        public async Task<string> FetchAccessTokenAsync(
            string baseUrl, string accessKey, string secretKey)
        {
            var response = await api.PostAsync<TokenApiRequest, TokenApiResponse>(
                Url(baseUrl, "/access_token"), accessKey, null,
                new TokenApiRequest { AccessKey = accessKey, SecretKey = secretKey },
                "access_token").ConfigureAwait(false);
            if (String.IsNullOrWhiteSpace(response.AccessToken))
                throw new InvalidOperationException("TCB response did not contain x-access-token.");
            return response.AccessToken;
        }

        public async Task<BasketValidationOutput> ValidateBasketAsync(
            string baseUrl, string accessKey, string accessToken,
            BasketValidationRequest request)
        {
            ValidateRequest(request);
            string mode = NormalizeMode(request.Mode);
            string domain = ValidateDomain(mode, request.RetailerEmailDomain);
            List<string> uniqueCoupons = DeduplicateCoupons(request.Coupons);
            List<ResolvedCoupon> coupons = await ResolveCouponsAsync(
                baseUrl, accessKey, accessToken, uniqueCoupons, mode, domain, true)
                .ConfigureAwait(false);
            return ruleEngine.Validate(new LocalValidationInput
            { Basket = request.Basket, Coupons = coupons });
        }

        public async Task<List<SerializedGs1Data>> ParseScannedGs1sAsync(
            string baseUrl, string accessKey, string accessToken,
            IList<string> scannedGs1s, string mode, string retailerEmailDomain)
        {
            if (scannedGs1s == null || scannedGs1s.Count == 0)
                throw new ArgumentException("At least one scanned GS1 is required.", "scannedGs1s");
            mode = NormalizeMode(mode);
            retailerEmailDomain = ValidateDomain(mode, retailerEmailDomain);
            var byIndex = new List<SerializedGs1Data>[scannedGs1s.Count];
            var pending = new List<IndexedGs1>();

            for (int i = 0; i < scannedGs1s.Count; i++)
            {
                byIndex[i] = new List<SerializedGs1Data>();
                List<SerializedGs1Data> local = Gs1Parser.TryParse(scannedGs1s[i]);
                if (local.Count > 0) byIndex[i].AddRange(local);
                else pending.Add(new IndexedGs1 { Index = i, Gs1 = scannedGs1s[i] });
            }

            var tasks = BuildBuckets(pending).Select(bucket => ResolveScanBucketAsync(
                baseUrl, accessKey, accessToken, bucket, mode, retailerEmailDomain)).ToArray();
            foreach (List<IndexedResolvedGs1> response in await Task.WhenAll(tasks).ConfigureAwait(false))
                foreach (IndexedResolvedGs1 item in response) byIndex[item.Index].Add(item.Value);
            return byIndex.SelectMany(values => values).ToList();
        }

        public async Task<RedeemResult> RedeemCouponsAsync(
            string baseUrl, string accessKey, string accessToken,
            IList<string> coupons, string mode, string retailerEmailDomain)
        {
            ValidateCoupons(coupons);
            mode = NormalizeMode(mode);
            retailerEmailDomain = ValidateDomain(mode, retailerEmailDomain);
            var tasks = Chunk(coupons).Select(values => api.PostAsync<RedeemRequest, RedeemResult>(
                Url(baseUrl, "/" + mode + "/redeem"), accessKey, accessToken,
                new RedeemRequest
                {
                    Gs1s = values, IncludeCheckDigit = "yes", NoPurchaseRequirement = "",
                    Offline = "", ClientTxnId = Guid.NewGuid().ToString(),
                    RetailerEmailDomain = retailerEmailDomain
                }, mode + "/redeem")).ToArray();
            return Merge(await Task.WhenAll(tasks).ConfigureAwait(false));
        }

        public async Task<Dictionary<string, string>> RollbackCouponsAsync(
            string baseUrl, string accessKey, string accessToken,
            IList<string> coupons, string mode)
        {
            ValidateCoupons(coupons);
            mode = NormalizeMode(mode);
            var tasks = coupons.Select(async gs1 => new KeyValuePair<string, string>(gs1,
                await api.DeleteAsync(
                    Url(baseUrl, "/" + mode + "/rollback/" + Uri.EscapeDataString(gs1)),
                    accessKey, accessToken, mode + "/rollback").ConfigureAwait(false))).ToArray();
            return (await Task.WhenAll(tasks).ConfigureAwait(false)).ToDictionary(x => x.Key, x => x.Value);
        }

        public void Dispose() { api.Dispose(); }

        private async Task<List<ResolvedCoupon>> ResolveCouponsAsync(
            string baseUrl, string key, string token, IList<string> coupons,
            string mode, string domain, bool includeRequirements)
        {
            ValidateCoupons(coupons);
            var pending = new List<IndexedGs1>();
            for (int index = 0; index < coupons.Count; index++)
            {
                List<SerializedGs1Data> parsed = Gs1Parser.TryParse(coupons[index]);
                if (parsed.Count > 1)
                {
                    for (int sequence = 0; sequence < parsed.Count; sequence++)
                        pending.Add(new IndexedGs1
                        { Index = index, Sequence = sequence, Gs1 = parsed[sequence].Gs1 });
                }
                else pending.Add(new IndexedGs1 { Index = index, Sequence = 0, Gs1 = coupons[index] });
            }
            var tasks = BuildBuckets(pending).Select(bucket => ResolveCouponBucketAsync(
                baseUrl, key, token, bucket, mode, domain, includeRequirements)).ToArray();
            List<IndexedResolvedCoupon>[] results = await Task.WhenAll(tasks).ConfigureAwait(false);
            return results.SelectMany(x => x).OrderBy(x => x.Index).ThenBy(x => x.Sequence)
                .Select(x => x.Value).ToList();
        }

        private async Task<List<IndexedResolvedCoupon>> ResolveCouponBucketAsync(
            string baseUrl, string key, string token, Bucket bucket,
            string mode, string domain, bool includeRequirements)
        {
            RedeemResult response = await PreprocessAsync(baseUrl, key, token,
                bucket.Items.Select(x => x.Gs1).ToList(), mode, domain,
                includeRequirements ? "" : "yes").ConfigureAwait(false);
            var output = new List<IndexedResolvedCoupon>();
            foreach (IndexedGs1 requested in bucket.Items)
            {
                IEnumerable<RedeemedCoupon> matches = requested.Gs1.Length == 16
                    ? response.NewlyRedeemed : response.NewlyRedeemed.Where(x => Matches(requested.Gs1, x));
                int sequence = requested.Sequence;
                foreach (RedeemedCoupon redeemed in matches)
                {
                    PurchaseRequirement requirement;
                    if (!response.MasterOfferFiles.TryGetValue(redeemed.MasterOfferFile, out requirement)) continue;
                    output.Add(new IndexedResolvedCoupon
                    { Index = requested.Index, Sequence = sequence++, Value = new ResolvedCoupon
                        { Gs1 = redeemed.Gs1, PurchaseRequirement = requirement } });
                }
            }
            return output;
        }

        private async Task<List<IndexedResolvedGs1>> ResolveScanBucketAsync(
            string baseUrl, string key, string token, Bucket bucket, string mode, string domain)
        {
            RedeemResult response = await PreprocessAsync(baseUrl, key, token,
                bucket.Items.Select(x => x.Gs1).ToList(), mode, domain, "yes").ConfigureAwait(false);
            var output = new List<IndexedResolvedGs1>();
            foreach (IndexedGs1 requested in bucket.Items)
            {
                IEnumerable<RedeemedCoupon> matches = requested.Gs1.Length == 16
                    ? response.NewlyRedeemed : response.NewlyRedeemed.Where(x => Matches(requested.Gs1, x));
                foreach (RedeemedCoupon item in matches)
                    output.Add(new IndexedResolvedGs1 { Index = requested.Index,
                        Value = new SerializedGs1Data { Gs1 = item.Gs1, BaseGs1 = item.MasterOfferFile, Validated = true } });
            }
            return output;
        }

        private Task<RedeemResult> PreprocessAsync(
            string baseUrl, string key, string token, List<string> gs1s,
            string mode, string domain, string noRequirement)
        {
            return api.PostAsync<RedeemRequest, RedeemResult>(
                Url(baseUrl, "/" + mode + "/redeem"), key, token,
                new RedeemRequest { Gs1s = gs1s, PreProcess = "yes", IncludeCheckDigit = "yes",
                    NoPurchaseRequirement = noRequirement, Offline = "", RetailerEmailDomain = domain },
                mode + "/redeem");
        }

        private static List<Bucket> BuildBuckets(List<IndexedGs1> values)
        {
            var buckets = values.Where(x => x.Gs1 != null && x.Gs1.Length == 16)
                .Select(x => new Bucket { Items = new List<IndexedGs1> { x } }).ToList();
            var other = values.Where(x => x.Gs1 == null || x.Gs1.Length != 16).ToList();
            for (int i = 0; i < other.Count; i += ChunkSize)
                buckets.Add(new Bucket { Items = other.GetRange(i, Math.Min(ChunkSize, other.Count - i)) });
            return buckets;
        }

        private static List<List<string>> Chunk(IList<string> values)
        {
            var chunks = new List<List<string>>();
            for (int i = 0; i < values.Count; i += ChunkSize)
                chunks.Add(values.Skip(i).Take(Math.Min(ChunkSize, values.Count - i)).ToList());
            return chunks;
        }

        private static RedeemResult Merge(IEnumerable<RedeemResult> responses)
        {
            var merged = new RedeemResult { Status = "success" };
            foreach (RedeemResult item in responses)
            {
                if (item.Status != "success") merged.Status = item.Status;
                merged.StatusCode = item.StatusCode;
                merged.Message = item.Message;
                merged.TotalGs1sProcessed += item.TotalGs1sProcessed;
                merged.NewlyRedeemed.AddRange(item.NewlyRedeemed ?? new List<RedeemedCoupon>());
                foreach (var pair in item.MasterOfferFiles ?? new Dictionary<string, PurchaseRequirement>())
                    merged.MasterOfferFiles[pair.Key] = pair.Value;
            }
            return merged;
        }

        private static bool Matches(string requested, RedeemedCoupon redeemed)
        { return redeemed.Gs1 == requested || redeemed.MasterOfferFile == Gs1Parser.MatchBase(requested); }
        private static string NormalizeMode(string mode)
        {
            if (String.IsNullOrWhiteSpace(mode) || mode.Equals("retailer", StringComparison.OrdinalIgnoreCase)) return "retailer";
            if (mode.Equals("accelerator", StringComparison.OrdinalIgnoreCase)) return "accelerator";
            throw new ArgumentException("Mode must be retailer or accelerator.", "mode");
        }
        private static string ValidateDomain(string mode, string domain)
        {
            if (mode != "accelerator") return null;
            if (String.IsNullOrWhiteSpace(domain)) throw new ArgumentException("Accelerator mode requires retailer_email_domain.");
            return domain.Trim();
        }
        private static void ValidateRequest(BasketValidationRequest request)
        { if (request == null || request.Basket == null || request.Coupons == null) throw new ArgumentException("Basket and coupons are required."); }
        private static void ValidateCoupons(IList<string> coupons)
        { if (coupons == null || coupons.Count == 0 || coupons.Any(String.IsNullOrWhiteSpace)) throw new ArgumentException("Coupons must contain at least one nonblank GS1."); }
        private static List<string> DeduplicateCoupons(IList<string> coupons)
        {
            var result = new List<string>();
            var seen = new HashSet<string>();
            foreach (string coupon in coupons)
                if (coupon.StartsWith("81122") || seen.Add(coupon)) result.Add(coupon);
            return result;
        }
        private static string Url(string baseUrl, string path)
        { return baseUrl.TrimEnd('/') + path; }

        private sealed class IndexedGs1 { public int Index; public int Sequence; public string Gs1; }
        private sealed class Bucket { public List<IndexedGs1> Items; }
        private sealed class IndexedResolvedCoupon { public int Index; public int Sequence; public ResolvedCoupon Value; }
        private sealed class IndexedResolvedGs1 { public int Index; public SerializedGs1Data Value; }
    }
}
