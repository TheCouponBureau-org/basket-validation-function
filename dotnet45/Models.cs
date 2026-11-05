using System.Collections.Generic;
using System.Runtime.Serialization;

namespace TheCouponBureau.BasketValidation
{
    [DataContract]
    public sealed class BasketValidationRequest
    {
        public BasketValidationRequest() { Basket = new List<BasketItem>(); Coupons = new List<string>(); }
        [DataMember(Name = "basket")] public List<BasketItem> Basket { get; set; }
        [DataMember(Name = "coupons")] public List<string> Coupons { get; set; }
        [DataMember(Name = "mode", EmitDefaultValue = false)] public string Mode { get; set; }
        [DataMember(Name = "retailer_email_domain", EmitDefaultValue = false)] public string RetailerEmailDomain { get; set; }
        [DataMember(Name = "enable_logging", EmitDefaultValue = false)] public bool? EnableLogging { get; set; }
    }

    [DataContract]
    public sealed class BasketItem
    {
        [DataMember(Name = "product_code")] public string ProductCode { get; set; }
        [DataMember(Name = "price")] public double Price { get; set; }
        [DataMember(Name = "quantity")] public int Quantity { get; set; }
        [DataMember(Name = "unit")] public string Unit { get; set; }
    }

    [DataContract]
    public sealed class BasketValidationOutput
    {
        public BasketValidationOutput() { AppliedCoupons = new List<AppliedCoupon>(); }
        [DataMember(Name = "discount_in_cents")] public long DiscountInCents { get; set; }
        [DataMember(Name = "applied_coupons")] public List<AppliedCoupon> AppliedCoupons { get; set; }
    }

    [DataContract]
    public sealed class AppliedCoupon
    {
        [DataMember(Name = "coupon_code")] public string CouponCode { get; set; }
        [DataMember(Name = "face_value_in_cents")] public long FaceValueInCents { get; set; }
        [DataMember(Name = "product_codes")] public ProductCodes ProductCodes { get; set; }
    }

    [DataContract]
    public sealed class ProductCodes
    {
        public ProductCodes() { Gtins = new List<string>(); }
        [DataMember(Name = "gtins")] public List<string> Gtins { get; set; }
    }

    [DataContract]
    public sealed class SerializedGs1Data
    {
        [DataMember(Name = "gs1")] public string Gs1 { get; set; }
        [DataMember(Name = "base_gs1")] public string BaseGs1 { get; set; }
        [DataMember(Name = "validated")] public bool Validated { get; set; }
    }

    [DataContract]
    internal sealed class ResolvedCoupon
    {
        [DataMember(Name = "gs1")] public string Gs1 { get; set; }
        [DataMember(Name = "purchase_requirement")] public PurchaseRequirement PurchaseRequirement { get; set; }
    }

    [DataContract]
    internal sealed class LocalValidationInput
    {
        [DataMember(Name = "basket")] public List<BasketItem> Basket { get; set; }
        [DataMember(Name = "coupons")] public List<ResolvedCoupon> Coupons { get; set; }
    }

    [DataContract]
    public sealed class PurchaseRequirement
    {
        [DataMember(Name = "primary_purchase_gtins", EmitDefaultValue = false)] public List<string> PrimaryPurchaseGtins { get; set; }
        [DataMember(Name = "primary_purchase_eans", EmitDefaultValue = false)] public List<string> PrimaryPurchaseEans { get; set; }
        [DataMember(Name = "excluded_primary_purchase_gtins", EmitDefaultValue = false)] public List<string> ExcludedPrimaryPurchaseGtins { get; set; }
        [DataMember(Name = "excluded_primary_purchase_eans", EmitDefaultValue = false)] public List<string> ExcludedPrimaryPurchaseEans { get; set; }
        [DataMember(Name = "primary_purchase_prefixed_code", EmitDefaultValue = false)] public object PrimaryPurchasePrefixedCode { get; set; }
        [DataMember(Name = "excluded_primary_purchase_prefixed_code", EmitDefaultValue = false)] public object ExcludedPrimaryPurchasePrefixedCode { get; set; }
        [DataMember(Name = "primary_purchase_save_value", EmitDefaultValue = false)] public long? PrimaryPurchaseSaveValue { get; set; }
        [DataMember(Name = "primary_purchase_requirements", EmitDefaultValue = false)] public long? PrimaryPurchaseRequirements { get; set; }
        [DataMember(Name = "primary_purchase_req_code", EmitDefaultValue = false)] public int? PrimaryPurchaseReqCode { get; set; }
        [DataMember(Name = "second_purchase_gtins", EmitDefaultValue = false)] public List<string> SecondPurchaseGtins { get; set; }
        [DataMember(Name = "second_purchase_eans", EmitDefaultValue = false)] public List<string> SecondPurchaseEans { get; set; }
        [DataMember(Name = "excluded_second_purchase_gtins", EmitDefaultValue = false)] public List<string> ExcludedSecondPurchaseGtins { get; set; }
        [DataMember(Name = "excluded_second_purchase_eans", EmitDefaultValue = false)] public List<string> ExcludedSecondPurchaseEans { get; set; }
        [DataMember(Name = "second_purchase_prefixed_code", EmitDefaultValue = false)] public object SecondPurchasePrefixedCode { get; set; }
        [DataMember(Name = "excluded_second_purchase_prefixed_code", EmitDefaultValue = false)] public object ExcludedSecondPurchasePrefixedCode { get; set; }
        [DataMember(Name = "second_purchase_save_value", EmitDefaultValue = false)] public long? SecondPurchaseSaveValue { get; set; }
        [DataMember(Name = "second_purchase_requirements", EmitDefaultValue = false)] public long? SecondPurchaseRequirements { get; set; }
        [DataMember(Name = "second_purchase_req_code", EmitDefaultValue = false)] public int? SecondPurchaseReqCode { get; set; }
        [DataMember(Name = "third_purchase_gtins", EmitDefaultValue = false)] public List<string> ThirdPurchaseGtins { get; set; }
        [DataMember(Name = "third_purchase_eans", EmitDefaultValue = false)] public List<string> ThirdPurchaseEans { get; set; }
        [DataMember(Name = "excluded_third_purchase_gtins", EmitDefaultValue = false)] public List<string> ExcludedThirdPurchaseGtins { get; set; }
        [DataMember(Name = "excluded_third_purchase_eans", EmitDefaultValue = false)] public List<string> ExcludedThirdPurchaseEans { get; set; }
        [DataMember(Name = "third_purchase_prefixed_code", EmitDefaultValue = false)] public object ThirdPurchasePrefixedCode { get; set; }
        [DataMember(Name = "excluded_third_purchase_prefixed_code", EmitDefaultValue = false)] public object ExcludedThirdPurchasePrefixedCode { get; set; }
        [DataMember(Name = "third_purchase_save_value", EmitDefaultValue = false)] public long? ThirdPurchaseSaveValue { get; set; }
        [DataMember(Name = "third_purchase_requirements", EmitDefaultValue = false)] public long? ThirdPurchaseRequirements { get; set; }
        [DataMember(Name = "third_purchase_req_code", EmitDefaultValue = false)] public int? ThirdPurchaseReqCode { get; set; }
        [DataMember(Name = "save_value_code", EmitDefaultValue = false)] public int? SaveValueCode { get; set; }
        [DataMember(Name = "applies_to_which_item", EmitDefaultValue = false)] public int? AppliesToWhichItem { get; set; }
        [DataMember(Name = "additional_purchase_rules_code", EmitDefaultValue = false)] public int? AdditionalPurchaseRulesCode { get; set; }
    }

    [DataContract]
    public sealed class RedeemedCoupon
    {
        [DataMember(Name = "gs1")] public string Gs1 { get; set; }
        [DataMember(Name = "master_offer_file")] public string MasterOfferFile { get; set; }
    }

    [DataContract]
    public sealed class RedeemResult
    {
        public RedeemResult() { NewlyRedeemed = new List<RedeemedCoupon>(); MasterOfferFiles = new Dictionary<string, PurchaseRequirement>(); }
        [DataMember(Name = "status")] public string Status { get; set; }
        [DataMember(Name = "status_code")] public string StatusCode { get; set; }
        [DataMember(Name = "newly_redeemed")] public List<RedeemedCoupon> NewlyRedeemed { get; set; }
        [DataMember(Name = "master_offer_files")] public Dictionary<string, PurchaseRequirement> MasterOfferFiles { get; set; }
        [DataMember(Name = "total_gs1s_processed")] public int TotalGs1sProcessed { get; set; }
        [DataMember(Name = "message")] public string Message { get; set; }
        [DataMember(Name = "execution_id", EmitDefaultValue = false)] public string ExecutionId { get; set; }
    }

    [DataContract]
    internal sealed class RedeemRequest
    {
        [DataMember(Name = "gs1s")] public List<string> Gs1s { get; set; }
        [DataMember(Name = "pre_process", EmitDefaultValue = false)] public string PreProcess { get; set; }
        [DataMember(Name = "include_check_digit")] public string IncludeCheckDigit { get; set; }
        [DataMember(Name = "no_purchase_requirement")] public string NoPurchaseRequirement { get; set; }
        [DataMember(Name = "offline")] public string Offline { get; set; }
        [DataMember(Name = "client_txn_id", EmitDefaultValue = false)] public string ClientTxnId { get; set; }
        [DataMember(Name = "retailer_email_domain", EmitDefaultValue = false)] public string RetailerEmailDomain { get; set; }
    }

    [DataContract]
    internal sealed class TokenApiRequest
    {
        [DataMember(Name = "access_key")] public string AccessKey { get; set; }
        [DataMember(Name = "secret_key")] public string SecretKey { get; set; }
    }

    [DataContract]
    internal sealed class TokenApiResponse
    {
        [DataMember(Name = "x-access-token")] public string AccessToken { get; set; }
    }
}
