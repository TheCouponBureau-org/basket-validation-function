package org.thecouponbureau.validate.basket.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * =====================================================
 * basketValidationResults
 * =====================================================
 *
 * This file contains all data models used in:
 * - Basket validation
 * - Coupon evaluation
 * - Discount calculation
 *
 * It acts as the central contract between:
 * - Input (API / UI / Test data)
 * - Processing services
 * - Output (validation result)
 */
public class basketValidationResults {

    // =====================================================
    // INPUT MODELS
    // =====================================================

    /**
     * Input payload for validation
     * Contains:
     * - Basket items
     * - Coupons to evaluate
     */
    public static class BasketValidationInput {
        public List<BasketItem> basket;
        public List<Coupon> coupons;
    }

    // =====================================================
    // OUTPUT MODELS
    // =====================================================

    /**
     * Final validation result
     */
    public static class ValidationResult {

        // Detailed output of basket validation
        public BasketValidationOutput basketValidationOutput;

        // True if some coupons could not be applied
        public boolean notAllCouponsConsumed;
    }

    /**
     * Output after applying coupons
     */
    public static class BasketValidationOutput {

        // Total discount applied (in cents)
        public long discountInCents;

        // List of applied coupons
        public List<AppliedCoupon> appliedCoupons;
    }

    /**
     * Represents a single applied coupon
     */
    public static class AppliedCoupon {

        // Coupon identifier
        public String couponCode;

        // Discount value applied
        public long faceValueInCents;

        // Mapping of purchase type → product codes
        // Example:
        // { "primary": ["A", "B"], "second": ["C"] }
        public Map<String, List<String>> productCodes;
    }

    // =====================================================
    // CORE DOMAIN MODELS
    // =====================================================

    /**
     * Represents a basket item
     */
    public static class BasketItem {
        public String productCode;     // Unique product identifier
        public double price;           // Price per unit
        public int quantity;           // Quantity in basket
        public String unit;            // Unit type (e.g., kg, pcs)
        public String productType;     // Category/type
        public String purchaseType;    // primary / second / third
        public Boolean purchaseReuse;  // Can this item be reused for multiple rules
    }

    /**
     * Coupon model
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coupon {
        public String gs1;                      // Coupon identifier
        public String baseGs1;                  // Base reference
        public PurchaseRequirement purchaseRequirement; // Rules
    }

    /**
     * Defines all purchase rules for a coupon
     */
    public static class PurchaseRequirement {

        // Discount type (flat, percentage, free item, etc.)
        public Integer saveValueCode;

        // Which purchase group discount applies to (0,1,2)
        public Integer appliesToWhichItem;

        // Rule combination logic:
        // null → only primary
        // 0 → OR
        // 1 → AND
        // 2 → Primary + (Secondary OR Third)
        public Integer additionalPurchaseRulesCode;

        // -------------------------
        // PRIMARY PURCHASE
        // -------------------------
        public Long primaryPurchaseSaveValue;
        public Long primaryPurchaseRequirements;
        public Integer primaryPurchaseReqCode;
        public List<String> primaryPurchaseGtins;
        public List<String> primaryPurchaseEans;
        public List<String> excludedPrimaryPurchaseGtins;
        public List<String> excludedPrimaryPurchaseEans;
        public Object primaryPurchasePrefixedCode;
        public Object excludedPrimaryPurchasePrefixedCode;

        // -------------------------
        // SECOND PURCHASE
        // -------------------------
        public Long secondPurchaseSaveValue;
        public Long secondPurchaseRequirements;
        public Integer secondPurchaseReqCode;
        public List<String> secondPurchaseGtins;
        public List<String> secondPurchaseEans;
        public List<String> excludedSecondPurchaseGtins;
        public List<String> excludedSecondPurchaseEans;
        public Object secondPurchasePrefixedCode;
        public Object excludedSecondPurchasePrefixedCode;

        // -------------------------
        // THIRD PURCHASE
        // -------------------------
        public Long thirdPurchaseSaveValue;
        public Long thirdPurchaseRequirements;
        public Integer thirdPurchaseReqCode;
        public List<String> thirdPurchaseGtins;
        public List<String> thirdPurchaseEans;
        public List<String> excludedThirdPurchaseGtins;
        public List<String> excludedThirdPurchaseEans;
        public Object thirdPurchasePrefixedCode;
        public Object excludedThirdPurchasePrefixedCode;
    }

    /**
     * Internal normalized purchase model
     * (Created via PurchaseFactory)
     */
    public static class Purchase {
        public Long saveValue;                // Discount value
        public Long requirements;             // Units or amount required
        public Integer reqCode;               // Type (unit-based, spend-based)
        public List<String> gtins;
        public List<String> excludedGtins;
        public List<String> eans;
        public List<String> excludedEans;

        // Prefix-based matching rules
        public Map<String, Range> prefixedCode = new HashMap<>();
        public Map<String, Range> excludedPrefixedCode = new HashMap<>();
    }

    /**
     * Wrapper for all purchase levels
     */
    public static class Purchases {
        public Purchase primaryPurchase;
        public Purchase secondPurchase;
        public Purchase thirdPurchase;
    }

    /**
     * Range for prefix matching
     */
    public static class Range {
        public String start;
        public String end;

        public Range() {}

        public Range(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    // =====================================================
    // RESULT / HELPER MODELS
    // =====================================================

    /**
     * Simple boolean wrapper
     */
    public static class Status {
        public boolean status;

        public Status(boolean status) {
            this.status = status;
        }
    }

    /**
     * Result of checking unit availability
     */
    public static class BasketHasUnitsResult {
        public boolean status;
        public List<BasketItem> basketItems;
    }

    /**
     * Result of evaluating a single purchase condition
     */
    public static class MeetsPurchaseRequirementsResult {
        public boolean status;
        public List<BasketItem> basketItems;
        public Integer unitsToPurchase;

        public MeetsPurchaseRequirementsResult(
                boolean status,
                List<BasketItem> basketItems,
                Integer unitsToPurchase) {

            this.status = status;
            this.basketItems = basketItems;
            this.unitsToPurchase = unitsToPurchase;
        }
    }

    /**
     * Final result of requirement evaluation
     */
    public static class MeetsRequirementsResult {

        public boolean status;

        // Items used to satisfy conditions
        public List<BasketItem> basketItems = new ArrayList<>();

        // Units required for each purchase level
        public Integer unitsToPurchase;
        public Integer unitsToPurchase2;
        public Integer unitsToPurchase3;

        public MeetsRequirementsResult() {}

        public MeetsRequirementsResult(
                boolean status,
                List<BasketItem> basketItems,
                Integer unitsToPurchase,
                Integer unitsToPurchase2,
                Integer unitsToPurchase3) {

            this.status = status;
            this.basketItems = basketItems != null ? basketItems : new ArrayList<>();
            this.unitsToPurchase = unitsToPurchase;
            this.unitsToPurchase2 = unitsToPurchase2;
            this.unitsToPurchase3 = unitsToPurchase3;
        }

        /**
         * Helper method for failure case
         */
        public static MeetsRequirementsResult negative() {
            MeetsRequirementsResult r = new MeetsRequirementsResult();
            r.status = false;
            r.basketItems = new ArrayList<>();
            return r;
        }
    }

    /**
     * Result of basket reduction (consumed vs remaining)
     */
    public static class ReduceBasketResult {
        public List<BasketItem> consumedBasket;
        public List<BasketItem> newBasket;
    }

    /**
     * Holds unit requirements for different purchase levels
     * (Used during basket reduction)
     */
    public static class UnitsToPurchaseHolder {

        private Integer unitsToPurchase;
        private Integer unitsToPurchase2;
        private Integer unitsToPurchase3;

        public UnitsToPurchaseHolder(
                Integer unitsToPurchase,
                Integer unitsToPurchase2,
                Integer unitsToPurchase3) {

            this.unitsToPurchase = unitsToPurchase;
            this.unitsToPurchase2 = unitsToPurchase2;
            this.unitsToPurchase3 = unitsToPurchase3;
        }

        /**
         * Get units by key
         */
        public Integer get(String key) {
            switch (key) {
                case "units_to_purchase": return unitsToPurchase;
                case "units_to_purchase2": return unitsToPurchase2;
                case "units_to_purchase3": return unitsToPurchase3;
                default: return null;
            }
        }

        /**
         * Update units by key
         */
        public void set(String key, Integer value) {
            switch (key) {
                case "units_to_purchase":
                    unitsToPurchase = value;
                    break;
                case "units_to_purchase2":
                    unitsToPurchase2 = value;
                    break;
                case "units_to_purchase3":
                    unitsToPurchase3 = value;
                    break;
            }
        }
    }
}