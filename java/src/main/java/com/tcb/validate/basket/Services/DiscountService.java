package com.tcb.validate.basket.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.tcb.validate.basket.helper.BasketHelper;
import com.tcb.validate.basket.model.basketValidationResults.BasketItem;
import com.tcb.validate.basket.model.basketValidationResults.Coupon;

public class DiscountService {

	// =========================
	// Discount calculation
	// =========================
	public static long getDiscountInCents(Coupon coupon, List<BasketItem> basketItems, boolean hasOnlyPrimaryPurchase,
			long newBasketTotalPrice, List<BasketItem> consumedBasket) {
		Integer saveValueCode = coupon.purchaseRequirement.saveValueCode != null
				? coupon.purchaseRequirement.saveValueCode
				: 0;

		Integer appliesToWhichItem = coupon.purchaseRequirement.appliesToWhichItem;
		if (hasOnlyPrimaryPurchase && appliesToWhichItem == null) {
			appliesToWhichItem = 0;
		}

		long discountInCents = 0;

		if (saveValueCode == 0) {
			discountInCents = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
					? coupon.purchaseRequirement.primaryPurchaseSaveValue
					: 0;

			if (appliesToWhichItem != null || coupon.purchaseRequirement.appliesToWhichItem == null) {
				if (newBasketTotalPrice < discountInCents) {
					return -1;
				}

				List<BasketItem> newBasketItems = applicableBasketItems(basketItems, appliesToWhichItem);

				if (consumedBasket != null && !consumedBasket.isEmpty()) {
					Set<String> consumedCodes = consumedBasket.stream().map(i -> i.productCode)
							.collect(Collectors.toSet());

					newBasketItems = newBasketItems.stream().filter(i -> consumedCodes.contains(i.productCode))
							.collect(Collectors.toList());
				}

				long qualifyingPurchasePrice = 0;
				for (BasketItem item : newBasketItems) {
					qualifyingPurchasePrice += BasketHelper.toCents(item.price) * item.quantity;
				}

				if (qualifyingPurchasePrice < discountInCents) {
					discountInCents = 0;
				}

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
		} else if (saveValueCode == 1) {
			long maxAmountToPurchase = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
					? coupon.purchaseRequirement.primaryPurchaseSaveValue
					: 0;

			List<BasketItem> newBasketItems = applicableBasketItems(basketItems, appliesToWhichItem);
			if (!newBasketItems.isEmpty()) {
				discountInCents = BasketHelper.toCents(newBasketItems.get(0).price);
			}

			if (maxAmountToPurchase != 0 && discountInCents > maxAmountToPurchase) {
				discountInCents = maxAmountToPurchase;
			}

		} else if (saveValueCode == 2) {
			long freePurchaseItemUnits = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
					? coupon.purchaseRequirement.primaryPurchaseSaveValue
					: 0;

			int index = 0;
			for (BasketItem item : basketItems) {
				if (Objects.equals(appliesToWhichItem, 0) && item.purchaseType != null) {
					continue;
				}
				if (Objects.equals(appliesToWhichItem, 1) && !"second_purchase".equals(item.purchaseType)) {
					continue;
				}
				if (Objects.equals(appliesToWhichItem, 2) && !"third_purchase".equals(item.purchaseType)) {
					continue;
				}

				for (int i = 0; i < item.quantity; i++) {
					if (index < freePurchaseItemUnits) {
						discountInCents += BasketHelper.toCents(item.price);
						index++;
					}
				}
			}

		} else if (saveValueCode == 6) {
			discountInCents = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
					? coupon.purchaseRequirement.primaryPurchaseSaveValue
					: 0;
		}

		return discountInCents;
	}

	public static List<BasketItem> applicableBasketItems(List<BasketItem> basketItems, Integer appliesToWhichItem) {
		List<BasketItem> result = new ArrayList<>();
		if (appliesToWhichItem == null) {
			result.addAll(basketItems);
		} else if (appliesToWhichItem == 0) {
			for (BasketItem item : basketItems) {
				if (item.purchaseType == null)
					result.add(item);
			}
		} else if (appliesToWhichItem == 1) {
			for (BasketItem item : basketItems) {
				if ("second_purchase".equals(item.purchaseType))
					result.add(item);
			}
		} else if (appliesToWhichItem == 2) {
			for (BasketItem item : basketItems) {
				if ("third_purchase".equals(item.purchaseType))
					result.add(item);
			}
		}
		return result;
	}
}
