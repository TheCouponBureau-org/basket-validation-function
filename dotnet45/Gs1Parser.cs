using System;
using System.Collections.Generic;

namespace TheCouponBureau.BasketValidation
{
    internal static class Gs1Parser
    {
        public static List<SerializedGs1Data> TryParse(string value)
        {
            var result = new List<SerializedGs1Data>();
            if (String.IsNullOrWhiteSpace(value) || value.Length < 34) return result;
            for (int i = 0; i < value.Length; i++) if (!Char.IsDigit(value[i])) return result;

            int index = 0;
            while (index < value.Length)
            {
                if (index + 6 > value.Length || value.Substring(index, 4) != "8112")
                    return new List<SerializedGs1Data>();
                int baseLength = 18 + (value[index + 5] - '0');
                int serializedLength = baseLength + 16;
                if (index + serializedLength > value.Length)
                    return new List<SerializedGs1Data>();
                string gs1 = value.Substring(index, serializedLength);
                result.Add(new SerializedGs1Data
                { Gs1 = gs1, BaseGs1 = gs1.Substring(0, baseLength), Validated = false });
                index += serializedLength;
            }
            return result;
        }

        public static string MatchBase(string requested)
        { return requested != null && requested.Length > 4 ? requested.Substring(0, requested.Length - 4) : null; }
    }
}
