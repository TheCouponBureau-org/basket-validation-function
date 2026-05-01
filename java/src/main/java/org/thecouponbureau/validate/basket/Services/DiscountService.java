package org.thecouponbureau.validate.basket.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.thecouponbureau.validate.basket.helper.BasketHelper;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;

public class DiscountService {

    // =====================================================
    // Main method to calculate discount for a given coupon
    // =====================================================
    public static long getDiscountInCents(
            Coupon coupon,
            List<BasketItem> basketItems,
            boolean hasOnlyPrimaryPurchase,
            long newBasketTotalPrice,
            List<BasketItem> consumedBasket) {

        // Determine the type of discount (default = 0)
        Integer saveValueCode = coupon.purchaseRequirement.saveValueCode != null
                ? coupon.purchaseRequirement.saveValueCode
                : 0;
        
        // Identify which basket items the coupon applies to
        Integer appliesToWhichItem = coupon.purchaseRequirement.appliesToWhichItem;
        
        // If only primary purchase exists and no target specified → default to primary
        if (hasOnlyPrimaryPurchase && appliesToWhichItem == null) {
            appliesToWhichItem = 0;
        }

        long discountInCents = 0;

        // =====================================================
        // CASE 0 → Flat discount amount
        // =====================================================
        if (saveValueCode == 0) {

            // Get flat discount value
            discountInCents = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue
                    : 0;

            // Apply discount only if applicable items are valid
            if (appliesToWhichItem != null || coupon.purchaseRequirement.appliesToWhichItem == null) {

                // If total basket value is less than discount → invalid coupon
                if (newBasketTotalPrice < discountInCents) {
                    return -1;
                }

                // Get items eligible for discount
                List<BasketItem> newBasketItems = applicableBasketItems(basketItems, appliesToWhichItem);

                // Filter items if consumed basket exists
                if (consumedBasket != null && !consumedBasket.isEmpty()) {
                	List<BasketItem> filteredItems = new ArrayList<>();
                	
                	// Iterate through eligible basket items
                	for (BasketItem newItem : newBasketItems) {
                	    boolean found = false;

                	    for (BasketItem consumedItem : consumedBasket) {
                	        if (Objects.equals(consumedItem.productCode, newItem.productCode)) {
                	            found = true;
                	            break;
                	        }
                	    }
                	 // Retain only items that participated in coupon consumption
                	    if (found) {
                	        filteredItems.add(newItem);
                	    }
                	}
                	// Replace with filtered list aligned to consumed items
                	newBasketItems = filteredItems;
                }

                // Calculate total price of qualifying items
                long qualifyingPurchasePrice = 0;
                for (BasketItem item : newBasketItems) {
                    qualifyingPurchasePrice += BasketHelper.toCents(item.price) * item.quantity;
                }

                // If qualifying price is less than discount → no discount
                if (qualifyingPurchasePrice < discountInCents) {
                    discountInCents = 0;
                }

                // Validate against consumed basket price
                if (consumedBasket != null && !consumedBasket.isEmpty()) {
                    long consumedBasketPrice = 0;
                    for (BasketItem item : consumedBasket) {
                        consumedBasketPrice += BasketHelper.toCents(item.price) * item.quantity;
                    }

                    if (consumedBasketPrice < discountInCents) {
                        discountInCents = 0;
                    }
                }
            }

        // =====================================================
        // CASE 1 → Discount equals item price (capped)
        // =====================================================
        } else if (saveValueCode == 1) {

            long maxAmountToPurchase = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue
                    : 0;
            

            // Get applicable items
            List<BasketItem> newBasketItems = applicableBasketItems(basketItems, appliesToWhichItem);
            
            
            // Take price of first applicable item as discount
            if (!newBasketItems.isEmpty()) {
                discountInCents = BasketHelper.toCents(newBasketItems.get(0).price);
            }

            // Apply maximum cap if defined
            if (maxAmountToPurchase != 0 && discountInCents > maxAmountToPurchase) {
                discountInCents = maxAmountToPurchase;
            }

        // =====================================================
        // CASE 2 → Free items (based on quantity)
        // =====================================================
        } else if (saveValueCode == 2) {

            long freePurchaseItemUnits = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue
                    : 0;

            int index = 0;

            // Iterate through basket items
            for (BasketItem item : basketItems) {

                // Filter based on purchase type
                if (Objects.equals(appliesToWhichItem, 0) && item.purchaseType != null) {
                    continue;
                }
                if (Objects.equals(appliesToWhichItem, 1) && !"second_purchase".equals(item.purchaseType)) {
                    continue;
                }
                if (Objects.equals(appliesToWhichItem, 2) && !"third_purchase".equals(item.purchaseType)) {
                    continue;
                }

                // Add free item discount based on quantity
                for (int i = 0; i < item.quantity; i++) {
                    if (index < freePurchaseItemUnits) {
                        discountInCents += BasketHelper.toCents(item.price);
                        index++;
                    }
                }
            }

        // =====================================================
        // CASE 6 → Direct discount value (no validation)
        // =====================================================
        } else if (saveValueCode == 6) {

            discountInCents = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue
                    : 0;
        }

        return discountInCents;
    }

    // =====================================================
    // Helper method: Filters basket items based on purchase type
    // =====================================================
    public static List<BasketItem> applicableBasketItems(
            List<BasketItem> basketItems,
            Integer appliesToWhichItem) {

        List<BasketItem> result = new ArrayList<>();

        // If null → apply to all items
        if (appliesToWhichItem == null) {
            result.addAll(basketItems);

        // Primary purchase items
        } else if (appliesToWhichItem == 0) {
            for (BasketItem item : basketItems) {
                if (item.purchaseType == null)
                    result.add(item);
            }

        // Second purchase items
        } else if (appliesToWhichItem == 1) {
            for (BasketItem item : basketItems) {
                if ("second_purchase".equals(item.purchaseType))
                    result.add(item);
            }

        // Third purchase items
        } else if (appliesToWhichItem == 2) {
            for (BasketItem item : basketItems) {
                if ("third_purchase".equals(item.purchaseType))
                    result.add(item);
            }
        }

        return result;
    }
}