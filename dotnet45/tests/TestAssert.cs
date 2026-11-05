using System;
using System.Collections.Generic;

namespace TheCouponBureau.BasketValidation.SmokeTests
{
    internal static class TestAssert
    {
        public static void Equal<T>(T expected, T actual, string message)
        {
            if (!EqualityComparer<T>.Default.Equals(expected, actual))
                throw new InvalidOperationException(
                    message + " Expected: " + expected + "; actual: " + actual + ".");
        }

        public static void True(bool condition, string message)
        {
            if (!condition) throw new InvalidOperationException(message);
        }
    }
}
