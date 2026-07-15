package org.thecouponbureau.validate.basket.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.Services.BasketReducerService;
import org.thecouponbureau.validate.basket.Services.TcbCouponResolutionService;
import org.thecouponbureau.validate.basket.Services.DiscountService;
import org.thecouponbureau.validate.basket.Services.RequirementService;
import org.thecouponbureau.validate.basket.helper.BasketHelper;
import org.thecouponbureau.validate.basket.helper.BasketHelper.Status;
import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationOutput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.MeetsRequirementsResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ReduceBasketResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.UnitsToPurchaseHolder;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationError;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

/**
 * Main orchestration entrypoint for basket validation.
 *
 * <p>The validator first normalizes the basket, optionally removes obviously
 * non-applicable input coupons that already include a purchase requirement,
 * optionally refreshes remaining coupon requirements through TCB, and then
 * applies coupons sequentially while consuming qualifying basket items.
 */
public class BasketValidator {

    private static final ObjectMapper LOG_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // =====================================================
    // Constants
    // =====================================================
    public static final Status NEGATIVE_STATUS = new Status(false);
    public static final Status POSITIVE_STATUS = new Status(true);

    /**
     * Validates a basket against GS1 coupon codes by first resolving them
     * through TCB and then running local basket validation on the resolved
     * purchase requirements.
     *
     * <p>This entry point requires TCB credentials and expects {@code coupons}
     * to be an array of GS1 strings only.
     */
    public static ValidationResult validateBasketHelper(
            BasketValidationInput basketValidationInput) {
        BasketValidationOutput defaultOutput = createEmptyOutput();

        if (basketValidationInput == null
                || basketValidationInput.basket == null
                || basketValidationInput.coupons == null) {

            return buildErrorResult(
                    defaultOutput,
                    "INVALID_INPUT",
                    "basket and coupons are required.",
                    null);
        }

        boolean enableLogging = Boolean.TRUE.equals(basketValidationInput.enableLogging);

        logValidationInput(basketValidationInput, enableLogging);

        ValidationError inputError =
                validateTcbBackedCouponInputs(
                        basketValidationInput.coupons,
                        basketValidationInput);
        if (inputError != null) {
            return buildErrorResult(
                    defaultOutput,
                    inputError.code,
                    inputError.message,
                    inputError.details);
        }
        
        basketValidationInput.coupons = new ArrayList<>(
                new LinkedHashSet<>(basketValidationInput.coupons)

        );

        List<Coupon> resolvedCoupons = TcbCouponResolutionService.resolveCoupons(
                basketValidationInput.tcbBaseUrl,
                basketValidationInput.tcbAccessKey,
                basketValidationInput.tcbAccessToken,
                toInternalCouponCodes(basketValidationInput.coupons),
                enableLogging
        );

        LocalBasketValidationInput localInput = new LocalBasketValidationInput();
        localInput.basket = basketValidationInput.basket;
        localInput.coupons = toInputCoupons(resolvedCoupons);
        localInput.enableLogging = basketValidationInput.enableLogging;

        return localBasketValidation(localInput);
    }

    /**
     * Validates a basket locally using coupons that already include purchase
     * requirements. This method never calls TCB.
     */
    public static ValidationResult localBasketValidation(
            LocalBasketValidationInput basketValidationInput) {
        BasketValidationOutput defaultOutput = createEmptyOutput();

        if (basketValidationInput == null
                || basketValidationInput.basket == null
                || basketValidationInput.coupons == null) {

            return buildErrorResult(
                    defaultOutput,
                    "INVALID_INPUT",
                    "basket and coupons are required.",
                    null);
        }

        boolean enableLogging = Boolean.TRUE.equals(basketValidationInput.enableLogging);
        logValidationInput(basketValidationInput, enableLogging);

        ValidationError inputError = validateLocalCouponInputs(basketValidationInput.coupons);
        if (inputError != null) {
            return buildErrorResult(
                    defaultOutput,
                    inputError.code,
                    inputError.message,
                    inputError.details);
        }

        return validateResolvedCoupons(
                BasketHelper.mergeBasketItems(basketValidationInput.basket),
                toInternalCoupons(basketValidationInput.coupons));
    }

    /**
     * Backward-compatible alias for callers using the earlier misspelled name.
     */
    public static ValidationResult localBasketBalication(
            LocalBasketValidationInput basketValidationInput) {
        return localBasketValidation(basketValidationInput);
    }

    private static BasketValidationOutput createEmptyOutput() {
        BasketValidationOutput output = new BasketValidationOutput();
        output.discountInCents = 0;
        output.appliedCoupons = new ArrayList<>();
        return output;
    }

    private static ValidationResult validateResolvedCoupons(
            List<BasketItem> normalizedBasket,
            List<Coupon> couponsToProcess) {
        BasketValidationOutput basketValidationOutput = createEmptyOutput();
        List<BasketItem> newBasket = normalizedBasket;

        for (Coupon coupon : couponsToProcess) {
            CouponApplicationResult couponApplicationResult =
                    applyCoupon(newBasket, coupon);

            if (couponApplicationResult == null) {
                continue;
            }

            newBasket = couponApplicationResult.remainingBasket;
            basketValidationOutput.appliedCoupons.add(
                    couponApplicationResult.appliedCoupon);
            basketValidationOutput.discountInCents +=
                    couponApplicationResult.appliedCoupon.faceValueInCents;
        }

        ValidationResult result = new ValidationResult();
        result.basketValidationOutput = basketValidationOutput;
        return result;
    }

    private static CouponApplicationResult applyCoupon(
            List<BasketItem> currentBasket,
            Coupon coupon) {

        if (coupon == null || coupon.purchaseRequirement == null) {
            return null;
        }

        long basketTotalInCents = calculateBasketTotalInCents(currentBasket);
        MeetsRequirementsResult meetsResult =
                RequirementService.meetsRequirements(currentBasket, coupon);

        if (meetsResult == null || !meetsResult.status) {
            return null;
        }

        boolean hasOnlyPrimaryPurchase =
                meetsResult.unitsToPurchase2 == null
                        && meetsResult.unitsToPurchase3 == null;

        long discountInCents = DiscountService.getDiscountInCents(
                coupon,
                meetsResult.basketItems,
                hasOnlyPrimaryPurchase,
                basketTotalInCents,
                new ArrayList<>());

        if (discountInCents <= 0
                || meetsResult.basketItems == null
                || meetsResult.basketItems.isEmpty()) {
            return null;
        }

        ReduceBasketResult reducedBasket = BasketReducerService.reduceBasket(
                currentBasket,
                meetsResult.basketItems,
                new UnitsToPurchaseHolder(
                        meetsResult.unitsToPurchase,
                        meetsResult.unitsToPurchase2,
                        meetsResult.unitsToPurchase3));

        List<BasketItem> consumedBasket = reducedBasket.consumedBasket;
        discountInCents = DiscountService.getDiscountInCents(
                coupon,
                meetsResult.basketItems,
                hasOnlyPrimaryPurchase,
                basketTotalInCents,
                consumedBasket);

        if (discountInCents <= 0) {
            return null;
        }

        AppliedCoupon appliedCoupon = new AppliedCoupon();
        appliedCoupon.couponCode = coupon.gs1;
        appliedCoupon.faceValueInCents = discountInCents;
        appliedCoupon.productCodes = BasketHelper.getProductCodes(consumedBasket);

        CouponApplicationResult result = new CouponApplicationResult();
        result.appliedCoupon = appliedCoupon;
        result.remainingBasket = reducedBasket.newBasket;
        return result;
    }

    private static long calculateBasketTotalInCents(List<BasketItem> basket) {
        long basketTotalInCents = 0L;

        for (BasketItem item : basket) {
            basketTotalInCents += BasketHelper.toCents(item.price) * item.quantity;
        }

        return basketTotalInCents;
    }

    private static boolean hasTcbCredentials(BasketValidationInput input) {
        return input != null
                && !isBlank(input.tcbBaseUrl)
                && !isBlank(input.tcbAccessKey)
                && !isBlank(input.tcbAccessToken);
    }

    private static void logValidationInput(
            Object basketValidationInput,
            boolean enableLogging) {

        if (!enableLogging || basketValidationInput == null) {
            return;
        }

        try {
            Object logInput = basketValidationInput;

            if (basketValidationInput instanceof BasketValidationInput) {
                BasketValidationInput input = (BasketValidationInput) basketValidationInput;
                BasketValidationInput redactedInput = new BasketValidationInput();
                redactedInput.basket = input.basket;
                redactedInput.coupons = input.coupons;
                redactedInput.tcbBaseUrl = input.tcbBaseUrl;
                redactedInput.tcbAccessKey = redactValue(input.tcbAccessKey);
                redactedInput.tcbAccessToken = redactValue(input.tcbAccessToken);
                redactedInput.enableLogging = input.enableLogging;
                logInput = redactedInput;
            }

            System.out.println("[BasketValidator] Validation input:");
            System.out.println(
                    LOG_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(logInput));
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

    private static List<Coupon> toInternalCouponCodes(List<String> couponCodes) {
        List<Coupon> coupons = new ArrayList<>();

        if (couponCodes == null) {
            return coupons;
        }

        for (String couponCode : couponCodes) {
            Coupon coupon = new Coupon();
            coupon.gs1 = couponCode;
            coupons.add(coupon);
        }

        return coupons;
    }

    private static List<InputCoupon> toInputCoupons(List<Coupon> coupons) {
        List<InputCoupon> inputCoupons = new ArrayList<>();

        if (coupons == null) {
            return inputCoupons;
        }

        for (Coupon coupon : coupons) {
            if (coupon == null) {
                continue;
            }

            InputCoupon inputCoupon = new InputCoupon();
            inputCoupon.gs1 = coupon.gs1;
            inputCoupon.purchaseRequirement = coupon.purchaseRequirement;
            inputCoupons.add(inputCoupon);
        }

        return inputCoupons;
    }

    private static ValidationError validateLocalCouponInputs(List<InputCoupon> inputCoupons) {
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

            if (inputCoupon.purchaseRequirement == null) {
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon purchase_requirement is required for localBasketValidation.",
                        buildCouponIndexDetails(index));
            }

            if (inputCoupon.additionalFields != null && !inputCoupon.additionalFields.isEmpty()) {
                Map<String, Object> details = buildCouponIndexDetails(index);
                details.put("invalid_fields", new ArrayList<>(inputCoupon.additionalFields.keySet()));
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon input only supports gs1 and purchase_requirement.",
                        details);
            }
        }

        return null;
    }

    private static ValidationError validateTcbBackedCouponInputs(
            List<String> couponCodes,
            BasketValidationInput input) {
        if (couponCodes == null) {
            return buildValidationError(
                    "INVALID_INPUT",
                    "coupons are required.",
                    null);
        }

        for (int index = 0; index < couponCodes.size(); index++) {
            if (isBlank(couponCodes.get(index))) {
                return buildValidationError(
                        "INVALID_COUPON_INPUT",
                        "coupon gs1 is required.",
                        buildCouponIndexDetails(index));
            }
        }

        if (!hasTcbCredentials(input)) {
            return buildValidationError(
                    "INVALID_INPUT",
                    "tcb_base_url, tcb_access_key, and tcb_access_token are required.",
                    null);
        }

        return null;
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
        result.error = buildValidationError(code, message, details);
        return result;
    }

    private static class CouponApplicationResult {
        private AppliedCoupon appliedCoupon;
        private List<BasketItem> remainingBasket;
    }
}
