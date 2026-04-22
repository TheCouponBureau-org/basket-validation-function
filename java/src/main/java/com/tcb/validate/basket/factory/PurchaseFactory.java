package com.tcb.validate.basket.factory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tcb.validate.basket.model.basketValidationResults.Purchase;
import com.tcb.validate.basket.model.basketValidationResults.PurchaseRequirement;
import com.tcb.validate.basket.model.basketValidationResults.Purchases;
import com.tcb.validate.basket.model.basketValidationResults.Range;

/**
 * =====================================================
 * PurchaseFactory
 * =====================================================
 *
 * Responsible for transforming raw PurchaseRequirement
 * (input model) into normalized Purchase objects
 * used by the rule engine.
 *
 * Key responsibilities:
 * - Map raw fields → structured Purchase model
 * - Normalize prefixed code rules
 * - Handle multiple formats of input (List / Map / String)
 */
public class PurchaseFactory {

    // =====================================================
    // Build Purchases object (primary, second, third)
    // =====================================================
    public static Purchases getPurchases(PurchaseRequirement purchaseRequirement) {

        Purchases purchases = new Purchases();

        // -------------------------
        // PRIMARY PURCHASE
        // -------------------------
        purchases.primaryPurchase = new Purchase();
        purchases.primaryPurchase.saveValue = purchaseRequirement.primaryPurchaseSaveValue;
        purchases.primaryPurchase.requirements = purchaseRequirement.primaryPurchaseRequirements;
        purchases.primaryPurchase.reqCode = purchaseRequirement.primaryPurchaseReqCode;
        purchases.primaryPurchase.gtins = purchaseRequirement.primaryPurchaseGtins;
        purchases.primaryPurchase.eans = purchaseRequirement.primaryPurchaseEans;
        purchases.primaryPurchase.excludedGtins = purchaseRequirement.excludedPrimaryPurchaseGtins;
        purchases.primaryPurchase.excludedEans = purchaseRequirement.excludedPrimaryPurchaseEans;

        // Transform prefix-based rules
        purchases.primaryPurchase.prefixedCode =
                transformPrefixedCode(purchaseRequirement.primaryPurchasePrefixedCode);

        purchases.primaryPurchase.excludedPrefixedCode =
                transformPrefixedCode(purchaseRequirement.excludedPrimaryPurchasePrefixedCode);

        // -------------------------
        // SECOND PURCHASE
        // -------------------------
        purchases.secondPurchase = new Purchase();
        purchases.secondPurchase.saveValue = purchaseRequirement.secondPurchaseSaveValue;
        purchases.secondPurchase.requirements = purchaseRequirement.secondPurchaseRequirements;
        purchases.secondPurchase.reqCode = purchaseRequirement.secondPurchaseReqCode;
        purchases.secondPurchase.gtins = purchaseRequirement.secondPurchaseGtins;
        purchases.secondPurchase.eans = purchaseRequirement.secondPurchaseEans;
        purchases.secondPurchase.excludedGtins = purchaseRequirement.excludedSecondPurchaseGtins;
        purchases.secondPurchase.excludedEans = purchaseRequirement.excludedSecondPurchaseEans;

        purchases.secondPurchase.prefixedCode =
                transformPrefixedCode(purchaseRequirement.secondPurchasePrefixedCode);

        purchases.secondPurchase.excludedPrefixedCode =
                transformPrefixedCode(purchaseRequirement.excludedSecondPurchasePrefixedCode);

        // -------------------------
        // THIRD PURCHASE
        // -------------------------
        purchases.thirdPurchase = new Purchase();
        purchases.thirdPurchase.saveValue = purchaseRequirement.thirdPurchaseSaveValue;
        purchases.thirdPurchase.requirements = purchaseRequirement.thirdPurchaseRequirements;
        purchases.thirdPurchase.reqCode = purchaseRequirement.thirdPurchaseReqCode;
        purchases.thirdPurchase.gtins = purchaseRequirement.thirdPurchaseGtins;
        purchases.thirdPurchase.eans = purchaseRequirement.thirdPurchaseEans;
        purchases.thirdPurchase.excludedGtins = purchaseRequirement.excludedThirdPurchaseGtins;
        purchases.thirdPurchase.excludedEans = purchaseRequirement.excludedThirdPurchaseEans;

        purchases.thirdPurchase.prefixedCode =
                transformPrefixedCode(purchaseRequirement.thirdPurchasePrefixedCode);

        purchases.thirdPurchase.excludedPrefixedCode =
                transformPrefixedCode(purchaseRequirement.excludedThirdPurchasePrefixedCode);

        return purchases;
    }

    // =====================================================
    // Transform prefixedCode into Map<String, Range>
    // =====================================================
    public static Map<String, Range> transformPrefixedCode(Object prefixedCode) {

        // Return empty map if nothing defined
        if (prefixedCode == null) {
            return new HashMap<>();
        }

        Map<String, Range> output = new HashMap<>();

        // =====================================================
        // CASE 1: prefixedCode is a List (e.g., ["gtin:xxx:100-200"])
        // =====================================================
        if (prefixedCode instanceof List<?>) {

            List<?> list = (List<?>) prefixedCode;

            for (Object obj : list) {

                if (obj instanceof String) {
                    String code = (String) obj;

                    // Expected format: key:xxx:start-end
                    if (code.contains(":")) {

                        Map<String, String[]> transformed = transformString(code);

                        for (Map.Entry<String, String[]> e : transformed.entrySet()) {
                            String[] range = e.getValue();

                            if (range.length >= 2) {
                                output.put(e.getKey(),
                                        new Range(range[0], range[1]));
                            }
                        }
                    }
                }
            }
            return output;
        }

        // =====================================================
        // CASE 2: prefixedCode is a Map
        // =====================================================
        if (prefixedCode instanceof Map<?, ?>) {

            Map<?, ?> map = (Map<?, ?>) prefixedCode;

            for (Map.Entry<?, ?> entry : map.entrySet()) {

                String property = String.valueOf(entry.getKey());
                Object codeObj = entry.getValue();

                // Case: key itself contains prefix rule
                if (property.contains(":")) {

                    Map<String, String[]> transformed =
                            transformString(property);

                    for (Map.Entry<String, String[]> e : transformed.entrySet()) {

                        String[] range = e.getValue();

                        if (range.length >= 2) {
                            output.put(e.getKey(),
                                    new Range(range[0], range[1]));
                        }
                    }
                    continue;
                }

                // Case: value is a list like ["100_200"]
                if (codeObj instanceof List<?>) {

                    List<?> codeList = (List<?>) codeObj;

                    if (!codeList.isEmpty()) {

                        String[] range =
                                String.valueOf(codeList.get(0)).split("_");

                        if (range.length >= 2) {
                            output.put(property,
                                    new Range(range[0], range[1]));
                        }
                    }
                }
            }
        }

        return output;
    }

    // =====================================================
    // Convert string → Map<key, [start, end]>
    // Example:
    // "gtin:xxx:100-200" → { gtin → [100, 200] }
    // =====================================================
    public static Map<String, String[]> transformString(String input) {

        String[] parts = input.split(":");

        // First part = key (e.g., "gtin")
        String key = parts[0];

        // Third part = range (e.g., "100-200")
        String[] values = parts[2].split("[-_]");

        Map<String, String[]> result = new HashMap<>();
        result.put(key, values);

        return result;
    }
}