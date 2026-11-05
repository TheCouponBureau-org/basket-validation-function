using System;
using System.IO;
using System.Reflection;
using Jint;

namespace TheCouponBureau.BasketValidation
{
    internal sealed class BasketRuleEngine
    {
        private readonly string script;

        public BasketRuleEngine()
        {
            using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(
                "TheCouponBureau.BasketValidation.rules.js"))
            using (var reader = new StreamReader(stream))
                { script = reader.ReadToEnd(); }
        }

        public BasketValidationOutput Validate(LocalValidationInput input)
        {
            var engine = new Engine();
            engine.Execute(script);
            string output = engine.Invoke("validateBasketJson", JsonCodec.Serialize(input)).AsString();
            return JsonCodec.Deserialize<BasketValidationOutput>(output);
        }
    }
}
