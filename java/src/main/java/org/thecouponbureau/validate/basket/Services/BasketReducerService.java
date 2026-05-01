package org.thecouponbureau.validate.basket.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ReduceBasketResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.UnitsToPurchaseHolder;

public class BasketReducerService {

    // =====================================================
    // Reduces basket items based on coupon unit requirements
    // =====================================================
    public static ReduceBasketResult reduceBasket(
            List<BasketItem> basket,
            List<BasketItem> allowedBasketItems,
            UnitsToPurchaseHolder unitsHolder) {

        // Holds remaining items after consumption
        List<BasketItem> newBasket = new ArrayList<>();

        // Holds items consumed for coupon eligibility
        List<BasketItem> consumedBasket = new ArrayList<>();

        // Iterate through each basket item
        for (BasketItem item : basket) {

            // Create a working copy (to avoid mutating original object)
            BasketItem basketItem = copyBasketItem(item);

            // Will store consumed portion of this item
            BasketItem consumedBasketItem = null;

            // Iterate over all unit requirement keys (primary, second, third purchase)
            for (String key : Arrays.asList(
                    "units_to_purchase",
                    "units_to_purchase2",
                    "units_to_purchase3")) {

                Integer unitsToPurchase = unitsHolder.get(key);

                // Only process if:
                // - units required exist
                // - still need units (>0)
                // - item still has quantity
                if (unitsToPurchase != null && unitsToPurchase > 0 && basketItem.quantity > 0) {

                    // Check if this item is eligible for the given purchase type
                    BasketItem allowedBasketItem =
                            allowedBasketItemsIncludes(allowedBasketItems, basketItem, key);

                    if (allowedBasketItem != null) {

                        // Case 1: Basket has more quantity than required
                        if (basketItem.quantity > unitsToPurchase) {

                            // Consume only required units
                            consumedBasketItem = copyBasketItem(basketItem);
                            consumedBasketItem.quantity = unitsToPurchase;

                            // Reduce remaining basket quantity
                            basketItem.quantity = basketItem.quantity - unitsToPurchase;

                            // Requirement fulfilled
                            unitsHolder.set(key, 0);

                        } else {
                            // Case 2: Basket has less or equal quantity than required

                            consumedBasketItem = copyBasketItem(basketItem);

                            // Reduce required units
                            unitsHolder.set(key, unitsToPurchase - basketItem.quantity);

                            // All quantity consumed
                            basketItem.quantity = 0;
                        }

                        // =====================================================
                        // Handle purchase reuse logic
                        // If allowed, consumed items can satisfy other unit requirements
                        // =====================================================
                        if (Boolean.TRUE.equals(allowedBasketItem.purchaseReuse)) {

                            for (String otherKey : Arrays.asList(
                                    "units_to_purchase",
                                    "units_to_purchase2",
                                    "units_to_purchase3")) {

                                Integer otherValue = unitsHolder.get(otherKey);

                                // Skip same key & ensure requirement exists
                                if (!otherKey.equals(key) && otherValue != null && otherValue > 0) {

                                    // Check eligibility for other purchase type
                                    BasketItem allowedBasketItemOther =
                                            allowedBasketItemsIncludes(
                                                    allowedBasketItems,
                                                    consumedBasketItem,
                                                    otherKey);

                                    if (allowedBasketItemOther != null && allowedBasketItemOther.quantity > 0) {

                                        // Reduce requirement using consumed quantity
                                        int reduceQuantityOther =
                                                Math.min(otherValue, consumedBasketItem.quantity);

                                        unitsHolder.set(otherKey, otherValue - reduceQuantityOther);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add consumed portion (if any)
            if (consumedBasketItem != null && consumedBasketItem.quantity > 0) {
                consumedBasket.add(consumedBasketItem);
            }

            // Add remaining portion to new basket
            if (basketItem.quantity > 0) {
                newBasket.add(basketItem);
            }
        }

        // Prepare result object
        ReduceBasketResult result = new ReduceBasketResult();
        result.consumedBasket = consumedBasket;
        result.newBasket = newBasket;

        return result;
    }

    // =====================================================
    // Checks if a basket item is allowed for a given purchase type
    // =====================================================
    public static BasketItem allowedBasketItemsIncludes(
            List<BasketItem> allowedBasketItems,
            BasketItem item,
            String keyUnitsToPurchase) {

        for (BasketItem allowedBasketItem : allowedBasketItems) {

            // Filter by purchase type
            if ("units_to_purchase".equals(keyUnitsToPurchase)
                    && allowedBasketItem.purchaseType != null) {
                continue;
            }

            if ("units_to_purchase2".equals(keyUnitsToPurchase)
                    && !"second_purchase".equals(allowedBasketItem.purchaseType)) {
                continue;
            }

            if ("units_to_purchase3".equals(keyUnitsToPurchase)
                    && !"third_purchase".equals(allowedBasketItem.purchaseType)) {
                continue;
            }

            // Match by product code
            if (Objects.equals(allowedBasketItem.productCode, item.productCode)) {
                return allowedBasketItem;
            }
        }

        return null;
    }

    // =====================================================
    // Creates a deep copy of BasketItem
    // (Prevents modifying original basket data)
    // =====================================================
    public static BasketItem copyBasketItem(BasketItem item) {
        BasketItem copy = new BasketItem();
        copy.productCode = item.productCode;
        copy.price = item.price;
        copy.quantity = item.quantity;
        copy.unit = item.unit;
        copy.productType = item.productType;
        copy.purchaseType = item.purchaseType;
        copy.purchaseReuse = item.purchaseReuse;
        return copy;
    }
}