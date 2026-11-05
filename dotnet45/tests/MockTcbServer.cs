using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace TheCouponBureau.BasketValidation.SmokeTests
{
    internal sealed class MockTcbServer : IDisposable
    {
        private const string CouponOne = "8112009988459000019133924009755364";
        private const string CouponTwo = "8112009988459000039133772240739897";
        private const string FetchCode = "8112009988459000";
        private const string FetchCoupon = "8112009988459000099133000000000001";

        private readonly TcpListener listener;
        private readonly CancellationTokenSource cancellation = new CancellationTokenSource();
        private readonly ConcurrentBag<RecordedRequest> requests = new ConcurrentBag<RecordedRequest>();
        private readonly ConcurrentBag<Task> clientTasks = new ConcurrentBag<Task>();
        private readonly Task serverTask;
        private int tokenRequestCount;

        public MockTcbServer()
        {
            listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            int port = ((IPEndPoint)listener.LocalEndpoint).Port;
            BaseUrl = "http://127.0.0.1:" + port;
            serverTask = Task.Run((Func<Task>)AcceptRequestsAsync);
        }

        public string BaseUrl { get; private set; }
        public IEnumerable<RecordedRequest> Requests { get { return requests; } }

        public void Dispose()
        {
            cancellation.Cancel();
            listener.Stop();
            try { serverTask.Wait(TimeSpan.FromSeconds(2)); }
            catch (AggregateException) { }
            try { Task.WaitAll(clientTasks.ToArray(), TimeSpan.FromSeconds(2)); }
            catch (AggregateException) { }
            cancellation.Dispose();
        }

        private async Task AcceptRequestsAsync()
        {
            while (!cancellation.IsCancellationRequested)
            {
                try
                {
                    TcpClient client = await listener.AcceptTcpClientAsync().ConfigureAwait(false);
                    clientTasks.Add(Task.Run(() => HandleClientAsync(client)));
                }
                catch (ObjectDisposedException) { return; }
                catch (SocketException)
                {
                    if (cancellation.IsCancellationRequested) return;
                    throw;
                }
            }
        }

        private async Task HandleClientAsync(TcpClient client)
        {
            using (client)
            using (NetworkStream stream = client.GetStream())
            {
                RecordedRequest request = await ReadRequestAsync(stream).ConfigureAwait(false);
                requests.Add(request);
                MockResponse response = CreateResponse(request);
                await WriteResponseAsync(stream, response).ConfigureAwait(false);
            }
        }

        private MockResponse CreateResponse(RecordedRequest request)
        {
            if (request.Path == "/access_token")
            {
                if (Interlocked.Increment(ref tokenRequestCount) == 1)
                    return new MockResponse(500, "{\"status\":\"error\"}");
                return new MockResponse(200,
                    "{\"status\":\"success\",\"x-access-token\":\"test-access-token\"}");
            }

            if (request.Method == "DELETE" &&
                request.Path.StartsWith("/retailer/rollback/", StringComparison.Ordinal))
                return new MockResponse(200,
                    "{\"status\":\"success\",\"message\":\"Rolled back\"}");

            if (request.Path == "/retailer/redeem" && request.Method == "POST")
            {
                if (request.Body.Contains("\"pre_process\":\"yes\"") &&
                    request.Body.Contains("\"no_purchase_requirement\":\"yes\"") &&
                    request.Body.Contains(FetchCode))
                    return new MockResponse(200, FetchCodeResponse());

                if (request.Body.Contains("\"pre_process\":\"yes\""))
                    return new MockResponse(200, ValidationResponse());

                return new MockResponse(200, RedemptionResponse());
            }

            return new MockResponse(404,
                "{\"status\":\"error\",\"message\":\"Not found\"}");
        }

        private static string ValidationResponse()
        {
            return "{\"status\":\"success\",\"status_code\":\"FULL_REDEMPTION\"," +
                "\"newly_redeemed\":[" +
                "{\"gs1\":\"" + CouponTwo + "\",\"master_offer_file\":\"811200998845900003\"}," +
                "{\"gs1\":\"" + CouponOne + "\",\"master_offer_file\":\"811200998845900001\"}]," +
                "\"master_offer_files\":{" +
                "\"811200998845900001\":{" + Requirement("037000930396") + "}," +
                "\"811200998845900003\":{" + Requirement("030772076835") + "}}," +
                "\"total_gs1s_processed\":2}";
        }

        private static string Requirement(string productCode)
        {
            return "\"primary_purchase_gtins\":[\"" + productCode + "\"]," +
                "\"primary_purchase_save_value\":100," +
                "\"primary_purchase_requirements\":1," +
                "\"primary_purchase_req_code\":0," +
                "\"save_value_code\":0";
        }

        private static string FetchCodeResponse()
        {
            return "{\"status\":\"success\",\"newly_redeemed\":[{" +
                "\"gs1\":\"" + FetchCoupon + "\"," +
                "\"master_offer_file\":\"811200998845900009\"}]," +
                "\"master_offer_files\":{},\"total_gs1s_processed\":1}";
        }

        private static string RedemptionResponse()
        {
            return "{\"status\":\"success\",\"status_code\":\"FULL_REDEMPTION\"," +
                "\"newly_redeemed\":[{" +
                "\"gs1\":\"" + CouponOne + "\"," +
                "\"master_offer_file\":\"811200998845900001\"}]," +
                "\"master_offer_files\":{},\"total_gs1s_processed\":1," +
                "\"message\":\"Redeemed 1 gs1(s)\"}";
        }

        private static async Task<RecordedRequest> ReadRequestAsync(NetworkStream stream)
        {
            var bytes = new List<byte>();
            var buffer = new byte[4096];
            int headerEnd = -1;

            while (headerEnd < 0)
            {
                int read = await stream.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false);
                if (read == 0)
                    throw new EndOfStreamException("HTTP request ended before headers.");
                for (int index = 0; index < read; index++) bytes.Add(buffer[index]);
                headerEnd = FindHeaderEnd(bytes);
            }

            string headersText = Encoding.ASCII.GetString(bytes.ToArray(), 0, headerEnd);
            string[] headerLines = headersText.Split(
                new[] { "\r\n" }, StringSplitOptions.None);
            string[] requestParts = headerLines[0].Split(' ');
            var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            for (int index = 1; index < headerLines.Length; index++)
            {
                int separator = headerLines[index].IndexOf(':');
                if (separator > 0)
                    headers[headerLines[index].Substring(0, separator).Trim()] =
                        headerLines[index].Substring(separator + 1).Trim();
            }

            int contentLength = 0;
            string contentLengthValue;
            if (headers.TryGetValue("Content-Length", out contentLengthValue))
                contentLength = Int32.Parse(contentLengthValue);

            int bodyStart = headerEnd + 4;
            while (bytes.Count - bodyStart < contentLength)
            {
                int read = await stream.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false);
                if (read == 0)
                    throw new EndOfStreamException("HTTP request body ended early.");
                for (int index = 0; index < read; index++) bytes.Add(buffer[index]);
            }

            return new RecordedRequest
            {
                Method = requestParts[0],
                Path = requestParts[1],
                Headers = headers,
                Body = Encoding.UTF8.GetString(bytes.ToArray(), bodyStart, contentLength)
            };
        }

        private static int FindHeaderEnd(List<byte> bytes)
        {
            for (int index = 0; index <= bytes.Count - 4; index++)
                if (bytes[index] == 13 && bytes[index + 1] == 10 &&
                    bytes[index + 2] == 13 && bytes[index + 3] == 10)
                    return index;
            return -1;
        }

        private static async Task WriteResponseAsync(
            NetworkStream stream, MockResponse response)
        {
            byte[] body = Encoding.UTF8.GetBytes(response.Body);
            string reason = response.StatusCode == 200 ? "OK" :
                response.StatusCode == 500 ? "Internal Server Error" : "Not Found";
            string headers = "HTTP/1.1 " + response.StatusCode + " " + reason + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + body.Length + "\r\n" +
                "Connection: close\r\n\r\n";
            byte[] headerBytes = Encoding.ASCII.GetBytes(headers);
            await stream.WriteAsync(headerBytes, 0, headerBytes.Length).ConfigureAwait(false);
            await stream.WriteAsync(body, 0, body.Length).ConfigureAwait(false);
        }

        internal sealed class RecordedRequest
        {
            public string Method { get; set; }
            public string Path { get; set; }
            public Dictionary<string, string> Headers { get; set; }
            public string Body { get; set; }
        }

        private sealed class MockResponse
        {
            public MockResponse(int statusCode, string body)
            { StatusCode = statusCode; Body = body; }
            public int StatusCode { get; private set; }
            public string Body { get; private set; }
        }
    }
}
