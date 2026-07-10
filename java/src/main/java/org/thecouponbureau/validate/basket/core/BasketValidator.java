package org.thecouponbureau.validate.basket.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.Services.BasketReducerService;
import org.thecouponbureau.validate.basket.Services.TcbCouponResolutionService;
import org.thecouponbureau.validate.basket.Services.DiscountService;
import org.thecouponbureau.validate.basket.Services.RequirementService;
import org.thecouponbureau.validate.basket.Services.TcbTokenService;
import org.thecouponbureau.validate.basket.helper.BasketHelper;
import org.thecouponbureau.validate.basket.helper.BasketHelper.Status;
import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationOutput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.MeetsRequirementsResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ReduceBasketResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.UnitsToPurchaseHolder;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationError;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

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

    private static final ObjectMapper LOG_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

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

            return buildErrorResult(
                    defaultOutput,
                    "INVALID_INPUT",
                    "basket and coupons are required.",
                    null);
        }

        // Initialize output
        BasketValidationOutput basketValidationOutput = new BasketValidationOutput();
        basketValidationOutput.discountInCents = 0;
        basketValidationOutput.appliedCoupons = new ArrayList<>();

        boolean enableLogging = Boolean.TRUE.equals(basketValidationInput.enableLogging);

        logValidationInput(basketValidationInput, enableLogging);

        ValidationError inputError = validateCouponInputs(basketValidationInput.coupons);
        if (inputError != null) {
            return buildErrorResult(
                    defaultOutput,
                    inputError.code,
                    inputError.message,
                    inputError.details);
        }

        List<Coupon> couponsToProcess = toInternalCoupons(basketValidationInput.coupons);
        List<BasketItem> normalizedInputBasket =
                BasketHelper.mergeBasketItems(basketValidationInput.basket);

        couponsToProcess = filterApplicableInputCoupons(
                normalizedInputBasket,
                couponsToProcess);

        if (hasCouponsToResolve(couponsToProcess)
                && hasTcbCredentials(basketValidationInput)) {
            String accessToken = TcbTokenService.getAccessToken(
                    basketValidationInput.tcbBaseUrl,
                    basketValidationInput.tcbAccessKey,
                    basketValidationInput.tcbSecretKey
            );

            couponsToProcess = TcbCouponResolutionService.resolveCoupons(
                    basketValidationInput.tcbBaseUrl,
                    basketValidationInput.tcbAccessKey,
                    accessToken,
                    couponsToProcess,
                    enableLogging
            );
        }

        // Step 1: Normalize basket (merge duplicates)
        List<BasketItem> newBasket = normalizedInputBasket;

        boolean notAllCouponsConsumed = false;
        int index = 0;

        // =====================================================
        // Process each coupon sequentially
        // =====================================================
        for (Coupon coupon : couponsToProcess) {
        	
        	
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
                        && index < couponsToProcess.size()) {
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

    private static boolean hasCouponsToResolve(List<Coupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return false;
        }

        for (Coupon coupon : coupons) {
            if (coupon != null && coupon.gs1 != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasTcbCredentials(BasketValidationInput input) {
        return input != null
                && !isBlank(input.tcbBaseUrl)
                && !isBlank(input.tcbAccessKey)
                && !isBlank(input.tcbSecretKey);
    }

    private static void logValidationInput(
            BasketValidationInput basketValidationInput,
            boolean enableLogging) {

        if (!enableLogging || basketValidationInput == null) {
            return;
        }

        try {
            BasketValidationInput logInput = new BasketValidationInput();
            logInput.basket = basketValidationInput.basket;
            logInput.coupons = basketValidationInput.coupons;
            logInput.tcbBaseUrl = basketValidationInput.tcbBaseUrl;
            logInput.tcbAccessKey = redactValue(basketValidationInput.tcbAccessKey);
            logInput.tcbSecretKey = redactValue(basketValidationInput.tcbSecretKey);
            logInput.enableLogging = basketValidationInput.enableLogging;

            System.out.println("[BasketValidator] Validation input:");
            System.out.println(LOG_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(logInput));
        } catch (Exception exception) {
            System.err.println("[BasketValidator] Unable to log validation input: "
                    + exception.getMessage());
        }
    }

    private static String redactValue(String value) {
        if (isBlank(value)) {
            return value;
        }

        return "REDACTED";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static List<Coupon> toInternalCoupons(List<InputCoupon> inputCoupons) {
        List<Coupon> coupons = new ArrayList<>();

        if (inputCoupons == null) {
            return coupons;
        }

        for (InputCoupon inputCoupon : inputCoupons) {
            Coupon coupon = new Coupon();
            if (inputCoupon != null) {
                coupon.gs1 = inputCoupon.gs1;
                coupon.purchaseRequirement = inputCoupon.purchaseRequirement;
            }
            coupons.add(coupon);
        }

        return coupons;
    }

    private static ValidationError validateCouponInputs(List<InputCoupon> inputCoupons) {
        if (inputCoupons == null) {
            return buildValidationError(
                    "INVALID_INPUT",
                    "coupons are required.",
                    null);
        }

        for (int index = 0; index < inputCoupons.size(); index++) {
            InputCoupon inputCoupon = inputCoupons.get(index);

            if (inputCoupon == null) {
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon entry cannot be null.",
                        buildCouponIndexDetails(index));
            }

            if (isBlank(inputCoupon.gs1)) {
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon gs1 is required.",
                        buildCouponIndexDetails(index));
            }

            if (inputCoupon.additionalFields != null && !inputCoupon.additionalFields.isEmpty()) {
                Map<String, Object> details = buildCouponIndexDetails(index);
                details.put("invalid_fields", new ArrayList<>(inputCoupon.additionalFields.keySet()));
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon input only supports gs1 and optional purchase_requirement.",
                        details);
            }
        }

        return null;
    }

    private static List<Coupon> filterApplicableInputCoupons(
            List<BasketItem> basket,
            List<Coupon> coupons) {

        List<Coupon> filteredCoupons = new ArrayList<>();

        if (coupons == null) {
            return filteredCoupons;
        }

        for (Coupon coupon : coupons) {
            if (coupon == null) {
                continue;
            }

            if (coupon.purchaseRequirement == null) {
                filteredCoupons.add(coupon);
                continue;
            }

            if (canCouponApplyToBasket(basket, coupon)) {
                filteredCoupons.add(coupon);
            }
        }

        return filteredCoupons;
    }

    private static boolean canCouponApplyToBasket(
            List<BasketItem> basket,
            Coupon coupon) {

        if (basket == null || coupon == null || coupon.purchaseRequirement == null) {
            return false;
        }

        long basketTotalPrice = 0L;
        for (BasketItem item : basket) {
            basketTotalPrice += BasketHelper.toCents(item.price) * item.quantity;
        }

        MeetsRequirementsResult meetsResult =
                RequirementService.meetsRequirements(basket, coupon);

        if (meetsResult == null || !meetsResult.status) {
            return false;
        }

        boolean hasOnlyPrimaryPurchase =
                meetsResult.unitsToPurchase2 == null
                        && meetsResult.unitsToPurchase3 == null;

        long discountInCents =
                DiscountService.getDiscountInCents(
                        coupon,
                        meetsResult.basketItems,
                        hasOnlyPrimaryPurchase,
                        basketTotalPrice,
                        new ArrayList<>());

        return discountInCents > 0;
    }

    private static Map<String, Object> buildCouponIndexDetails(int index) {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("coupon_index", index);
        return details;
    }

    private static ValidationError buildValidationError(
            String code,
            String message,
            Map<String, Object> details) {
        ValidationError error = new ValidationError();
        error.code = code;
        error.message = message;
        error.details = details;
        return error;
    }

    private static ValidationResult buildErrorResult(
            BasketValidationOutput defaultOutput,
            String code,
            String message,
            Map<String, Object> details) {
        ValidationResult result = new ValidationResult();
        result.basketValidationOutput = defaultOutput;
        result.notAllCouponsConsumed = false;
        result.error = buildValidationError(code, message, details);
        return result;
    }
}
