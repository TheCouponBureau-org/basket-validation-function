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
     * Validates a basket against the provided coupons.
     *
     * <p>If TCB credentials are present, coupons that still need server-side
     * resolution are first sent through the TCB pre-process redeem flow so the
     * SDK can validate coupon state and hydrate purchase requirements before the
     * final basket pass.
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

        BasketValidationOutput basketValidationOutput = createEmptyOutput();

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

        List<BasketItem> newBasket = BasketHelper.mergeBasketItems(
                basketValidationInput.basket);
        List<Coupon> couponsToProcess = prepareCouponsForValidation(
                basketValidationInput,
                newBasket,
                enableLogging);

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

    private static BasketValidationOutput createEmptyOutput() {
        BasketValidationOutput output = new BasketValidationOutput();
        output.discountInCents = 0;
        output.appliedCoupons = new ArrayList<>();
        return output;
    }

    private static List<Coupon> prepareCouponsForValidation(
            BasketValidationInput input,
            List<BasketItem> normalizedInputBasket,
            boolean enableLogging) {

        List<Coupon> couponsToProcess = toInternalCoupons(input.coupons);
        couponsToProcess = filterApplicableInputCoupons(
                normalizedInputBasket,
                couponsToProcess);

        if (!hasCouponsToResolve(couponsToProcess) || !hasTcbCredentials(input)) {
            return couponsToProcess;
        }

        return TcbCouponResolutionService.resolveCoupons(
                input.tcbBaseUrl,
                input.tcbAccessKey,
                input.tcbAccessToken,
                couponsToProcess,
                enableLogging
        );
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
                && !isBlank(input.tcbAccessToken);
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
            logInput.tcbAccessToken = redactValue(basketValidationInput.tcbAccessToken);
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

            // Only gs1 and purchaseRequirement are part of the public coupon
            // input contract. Everything else is rejected before processing.
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

            // Keep only coupons that can currently apply to the basket.
            // Non-applicable input coupons are intentionally removed here so
            // they do not participate in the later TCB resolution step.
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

        // This mirrors the early validation rules without consuming basket
        // state. It is only used as a prefilter for coupons that already came
        // with purchaseRequirement in the input payload.
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
        result.error = buildValidationError(code, message, details);
        return result;
    }

    private static class CouponApplicationResult {
        private AppliedCoupon appliedCoupon;
        private List<BasketItem> remainingBasket;
    }
}
