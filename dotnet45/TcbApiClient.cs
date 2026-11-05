using System;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

namespace TheCouponBureau.BasketValidation
{
    internal sealed class TcbApiClient : IDisposable
    {
        private static readonly int[] BackoffMilliseconds = { 50, 100, 200 };
        private readonly HttpClient httpClient = new HttpClient();

        static TcbApiClient()
        {
            ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072;
        }

        public async Task<TResponse> PostAsync<TRequest, TResponse>(
            string url, string accessKey, string accessToken, TRequest body, string operation)
        {
            string json = JsonCodec.Serialize(body);
            string response = await SendAsync(
                delegate
                {
                    var request = new HttpRequestMessage(HttpMethod.Post, url);
                    request.Content = new StringContent(json, Encoding.UTF8, "application/json");
                    AddHeaders(request, accessKey, accessToken);
                    return request;
                }, operation).ConfigureAwait(false);
            return JsonCodec.Deserialize<TResponse>(response);
        }

        public Task<string> DeleteAsync(
            string url, string accessKey, string accessToken, string operation)
        {
            return SendAsync(
                delegate
                {
                    var request = new HttpRequestMessage(HttpMethod.Delete, url);
                    AddHeaders(request, accessKey, accessToken);
                    return request;
                }, operation);
        }

        public void Dispose() { httpClient.Dispose(); }

        private async Task<string> SendAsync(
            Func<HttpRequestMessage> requestFactory, string operation)
        {
            Exception lastError = null;
            HttpStatusCode lastStatus = 0;
            string lastBody = null;

            for (int attempt = 0; attempt <= BackoffMilliseconds.Length; attempt++)
            {
                try
                {
                    using (HttpRequestMessage request = requestFactory())
                    using (HttpResponseMessage response =
                        await httpClient.SendAsync(request).ConfigureAwait(false))
                    {
                        lastStatus = response.StatusCode;
                        lastBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                        if (response.IsSuccessStatusCode) return lastBody;
                    }
                }
                catch (Exception error) { lastError = error; }

                if (attempt < BackoffMilliseconds.Length)
                    await Task.Delay(BackoffMilliseconds[attempt]).ConfigureAwait(false);
            }

            if (lastStatus != 0) throw new TcbNativeException(operation, lastStatus, lastBody);
            throw new InvalidOperationException("TCB operation failed after retries: " + operation, lastError);
        }

        private static void AddHeaders(
            HttpRequestMessage request, string accessKey, string accessToken)
        {
            request.Headers.TryAddWithoutValidation("x-api-key", accessKey);
            if (!String.IsNullOrWhiteSpace(accessToken))
                request.Headers.TryAddWithoutValidation("x-access-token", accessToken);
        }
    }
}
