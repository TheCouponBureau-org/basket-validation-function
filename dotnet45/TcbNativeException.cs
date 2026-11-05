using System;
using System.Net;

namespace TheCouponBureau.BasketValidation
{
    [Serializable]
    public sealed class TcbNativeException : Exception
    {
        public TcbNativeException(string operation, HttpStatusCode statusCode, string responseBody)
            : base("TCB operation failed: " + operation + " (HTTP " + (int)statusCode + ").")
        { Operation = operation; StatusCode = statusCode; ResponseBody = responseBody; }
        public string Operation { get; private set; }
        public HttpStatusCode StatusCode { get; private set; }
        public string ResponseBody { get; private set; }
    }
}
