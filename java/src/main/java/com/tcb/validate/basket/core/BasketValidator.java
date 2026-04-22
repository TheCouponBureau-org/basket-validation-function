package com.tcb.validate.basket.core;

import java.util.*;

import com.tcb.validate.basket.helper.BasketHelper;
import com.tcb.validate.basket.helper.BasketHelper.Status;
import com.tcb.validate.basket.model.basketValidationResults.AppliedCoupon;
import com.tcb.validate.basket.model.basketValidationResults.BasketItem;
import com.tcb.validate.basket.model.basketValidationResults.BasketValidationInput;
import com.tcb.validate.basket.model.basketValidationResults.BasketValidationOutput;
import com.tcb.validate.basket.model.basketValidationResults.Coupon;
import com.tcb.validate.basket.model.basketValidationResults.MeetsRequirementsResult;
import com.tcb.validate.basket.model.basketValidationResults.ReduceBasketResult;
import com.tcb.validate.basket.model.basketValidationResults.UnitsToPurchaseHolder;
import com.tcb.validate.basket.model.basketValidationResults.ValidationResult;

import com.tcb.validate.basket.Services.BasketReducerService;
import com.tcb.validate.basket.Services.DiscountService;
import com.tcb.validate.basket.Services.RequirementService;

/**
 * =====================================================
 * BasketValidator
 * =====================================================
 *
 * Main orchestration engine for coupon validation.
 *
 * Flow:
 * 1. Normalize basket (merge duplicates)
 * 2. Iterate coupons one by one
 * 3. Check eligibility (RequirementService)
 * 4. Calculate discount (DiscountService)
 * 5. Reduce basket (consume items)
 * 6. Accumulate results
 */
public class BasketValidator {

    // =====================================================
    // Constants
    // =====================================================
    public static final Status NEGATIVE_STATUS = new Status(false);
    public static final Status POSITIVE_STATUS = new Status(true);

    // =====================================================
    // Main public API
    // =====================================================
    public static ValidationResult validateBasketHelper(
            BasketValidationInput basketValidationInput) {

        // Default empty output
        BasketValidationOutput defaultOutput = new BasketValidationOutput();
        defaultOutput.discountInCents = 0;
        defaultOutput.appliedCoupons = new ArrayList<>();

        // =====================================================
        // Input validation
        // =====================================================
        if (basketValidationInput == null
                || basketValidationInput.basket == null
                || basketValidationInput.coupons == null) {

            ValidationResult result = new ValidationResult();
            result.basketValidationOutput = defaultOutput;
            result.notAllCouponsConsumed = false;
            return result;
        }

        // Initialize output
        BasketValidationOutput basketValidationOutput = new BasketValidationOutput();
        basketValidationOutput.discountInCents = 0;
        basketValidationOutput.appliedCoupons = new ArrayList<>();

        // Step 1: Normalize basket (merge duplicates)
        List<BasketItem> newBasket =
                BasketHelper.mergeBasketItems(basketValidationInput.basket);

        boolean notAllCouponsConsumed = false;
        int index = 0;

        // =====================================================
        // Process each coupon sequentially
        // =====================================================
        for (Coupon coupon : basketValidationInput.coupons) {
            index++;

            // Skip invalid coupon
            if (coupon == null || coupon.purchaseRequirement == null) {
                System.err.println("Coupon does not have purchase requirement");
                continue;
            }

            // =====================================================
            // Step 2: Calculate current basket total
            // =====================================================
            long newBasketTotalPrice = 0;

            for (BasketItem item : newBasket) {
                newBasketTotalPrice +=
                        BasketHelper.toCents(item.price) * item.quantity;
            }

            // =====================================================
            // Step 3: Check if basket meets coupon requirements
            // =====================================================
            MeetsRequirementsResult meetsResult =
                    RequirementService.meetsRequirements(newBasket, coupon);

            if (meetsResult.status) {

                // Check if only primary purchase exists
                boolean hasOnlyPrimaryPurchase =
                        meetsResult.unitsToPurchase2 == null &&
                        meetsResult.unitsToPurchase3 == null;

                // =====================================================
                // Step 4: Initial discount calculation (pre-consumption)
                // =====================================================
                long discountInCents =
                        DiscountService.getDiscountInCents(
                                coupon,
                                meetsResult.basketItems,
                                hasOnlyPrimaryPurchase,
                                newBasketTotalPrice,
                                new ArrayList<>()
                        );

                // Skip if no valid discount
                if (discountInCents <= 0) {
                    continue;
                }

                int oldBasketUnits =
                        BasketHelper.basketUnits(newBasket);

                // Safety check
                if (meetsResult.basketItems == null
                        || meetsResult.basketItems.isEmpty()) {
                    continue;
                }

                // =====================================================
                // Step 5: Reduce basket (consume items for coupon)
                // =====================================================
                ReduceBasketResult reducedBasket =
                        BasketReducerService.reduceBasket(
                                newBasket,
                                meetsResult.basketItems,
                                new UnitsToPurchaseHolder(
                                        meetsResult.unitsToPurchase,
                                        meetsResult.unitsToPurchase2,
                                        meetsResult.unitsToPurchase3
                                )
                        );

                List<BasketItem> consumedBasket =
                        reducedBasket.consumedBasket;

                // =====================================================
                // Step 6: Recalculate discount (post-consumption)
                // (Important: ensures discount is based on actual consumed items)
                // =====================================================
                discountInCents =
                        DiscountService.getDiscountInCents(
                                coupon,
                                meetsResult.basketItems,
                                hasOnlyPrimaryPurchase,
                                newBasketTotalPrice,
                                consumedBasket
                        );

                // Update remaining basket
                newBasket = reducedBasket.newBasket;

                int newBasketUnits =
                        BasketHelper.basketUnits(newBasket);

                // =====================================================
                // Step 7: Build applied coupon result
                // =====================================================
                AppliedCoupon appliedCoupon = new AppliedCoupon();

                appliedCoupon.couponCode = coupon.gs1;
                appliedCoupon.faceValueInCents = discountInCents;

                // Group consumed product codes
                appliedCoupon.productCodes =
                        BasketHelper.getProductCodes(consumedBasket);

                basketValidationOutput.appliedCoupons.add(appliedCoupon);

                // Add to total discount
                if (discountInCents > 0) {
                    basketValidationOutput.discountInCents += discountInCents;
                }

                // =====================================================
                // Step 8: Check if basket exhausted before all coupons used
                // =====================================================
                if (newBasketUnits == 0
                        && index < basketValidationInput.coupons.size()) {
                    notAllCouponsConsumed = true;
                }
            }
        }

        // =====================================================
        // Final result
        // =====================================================
        ValidationResult result = new ValidationResult();
        result.basketValidationOutput = basketValidationOutput;
        result.notAllCouponsConsumed = notAllCouponsConsumed;

        return result;
    }
}