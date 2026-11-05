using System;
using System.IO;
using System.Runtime.Serialization.Json;
using System.Text;

namespace TheCouponBureau.BasketValidation
{
    internal static class JsonCodec
    {
        private static DataContractJsonSerializer CreateSerializer(Type type)
        {
            return new DataContractJsonSerializer(
                type,
                new DataContractJsonSerializerSettings { UseSimpleDictionaryFormat = true });
        }

        public static string Serialize<T>(T value)
        {
            var serializer = CreateSerializer(typeof(T));
            using (var stream = new MemoryStream())
            { serializer.WriteObject(stream, value); return Encoding.UTF8.GetString(stream.ToArray()); }
        }

        public static T Deserialize<T>(string json)
        {
            var serializer = CreateSerializer(typeof(T));
            using (var stream = new MemoryStream(Encoding.UTF8.GetBytes(json ?? String.Empty)))
            { return (T)serializer.ReadObject(stream); }
        }
    }
}
