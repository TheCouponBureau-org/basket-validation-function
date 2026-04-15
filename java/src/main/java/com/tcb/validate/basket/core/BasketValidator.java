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

public class BasketValidator {

	// =========================
	// Constants
	// =========================
	public static final Status NEGATIVE_STATUS = new Status(false);
	public static final Status POSITIVE_STATUS = new Status(true);

	// =========================
	// Main public API
	// =========================
	public static ValidationResult validateBasketHelper(BasketValidationInput basketValidationInput) {

		BasketValidationOutput defaultOutput = new BasketValidationOutput();
		defaultOutput.discountInCents = 0;
		defaultOutput.appliedCoupons = new ArrayList<>();

		if (basketValidationInput == null || basketValidationInput.basket == null
				|| basketValidationInput.coupons == null) {
			ValidationResult result = new ValidationResult();
			result.basketValidationOutput = defaultOutput;
			result.notAllCouponsConsumed = false;
			return result;
		}

		BasketValidationOutput basketValidationOutput = new BasketValidationOutput();
		basketValidationOutput.discountInCents = 0;
		basketValidationOutput.appliedCoupons = new ArrayList<>();

		List<BasketItem> newBasket = BasketHelper.mergeBasketItems(basketValidationInput.basket);
		boolean notAllCouponsConsumed = false;
		int index = 0;

		for (Coupon coupon : basketValidationInput.coupons) {
			index++;

			if (coupon == null || coupon.purchaseRequirement == null) {
				System.err.println("Coupon does not have purchase requirement");
				continue;
			}

			long newBasketTotalPrice = 0;
			for (BasketItem item : newBasket) {
				newBasketTotalPrice += BasketHelper.toCents(item.price) * item.quantity;
			}

			MeetsRequirementsResult meetsResult = RequirementService.meetsRequirements(newBasket, coupon);

			if (meetsResult.status) {
				boolean hasOnlyPrimaryPurchase = meetsResult.unitsToPurchase2 == null
						&& meetsResult.unitsToPurchase3 == null;

				long discountInCents = DiscountService.getDiscountInCents(coupon, meetsResult.basketItems,
						hasOnlyPrimaryPurchase, newBasketTotalPrice, new ArrayList<>());

				if (discountInCents <= 0) {
					continue;
				}

				int oldBasketUnits = BasketHelper.basketUnits(newBasket);

				if (meetsResult.basketItems == null || meetsResult.basketItems.isEmpty()) {
					continue;
				}

				ReduceBasketResult reducedBasket = BasketReducerService.reduceBasket(newBasket, meetsResult.basketItems,
						new UnitsToPurchaseHolder(meetsResult.unitsToPurchase, meetsResult.unitsToPurchase2,
								meetsResult.unitsToPurchase3));

				List<BasketItem> consumedBasket = reducedBasket.consumedBasket;

				discountInCents = DiscountService.getDiscountInCents(coupon, meetsResult.basketItems,
						hasOnlyPrimaryPurchase, newBasketTotalPrice, consumedBasket);

				newBasket = reducedBasket.newBasket;

				int newBasketUnits = BasketHelper.basketUnits(newBasket);

				AppliedCoupon appliedCoupon = new AppliedCoupon();
				appliedCoupon.couponCode = coupon.gs1;
				appliedCoupon.faceValueInCents = discountInCents;
				appliedCoupon.productCodes = BasketHelper.getProductCodes(consumedBasket);

				basketValidationOutput.appliedCoupons.add(appliedCoupon);

				if (discountInCents > 0) {
					basketValidationOutput.discountInCents += discountInCents;
				}

				if (newBasketUnits == 0 && index < basketValidationInput.coupons.size()) {
					notAllCouponsConsumed = true;
				}
			}
		}

		ValidationResult result = new ValidationResult();
		result.basketValidationOutput = basketValidationOutput;
		result.notAllCouponsConsumed = notAllCouponsConsumed;
		return result;
	}
}