package com.tcb.validate.basket.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.tcb.validate.basket.model.basketValidationResults.BasketItem;
import com.tcb.validate.basket.model.basketValidationResults.ReduceBasketResult;
import com.tcb.validate.basket.model.basketValidationResults.UnitsToPurchaseHolder;

public class BasketReducerService {

	// =========================
	// Basket reduction
	// =========================
	public static ReduceBasketResult reduceBasket(List<BasketItem> basket, List<BasketItem> allowedBasketItems,
			UnitsToPurchaseHolder unitsHolder) {
		List<BasketItem> newBasket = new ArrayList<>();
		List<BasketItem> consumedBasket = new ArrayList<>();

		for (BasketItem item : basket) {
			BasketItem basketItem = copyBasketItem(item);
			BasketItem consumedBasketItem = null;

			for (String key : Arrays.asList("units_to_purchase", "units_to_purchase2", "units_to_purchase3")) {
				Integer unitsToPurchase = unitsHolder.get(key);

				if (unitsToPurchase != null && unitsToPurchase > 0 && basketItem.quantity > 0) {
					BasketItem allowedBasketItem = allowedBasketItemsIncludes(allowedBasketItems, basketItem, key);

					if (allowedBasketItem != null) {
						if (basketItem.quantity > unitsToPurchase) {
							consumedBasketItem = copyBasketItem(basketItem);
							consumedBasketItem.quantity = unitsToPurchase;

							basketItem.quantity = basketItem.quantity - unitsToPurchase;
							unitsHolder.set(key, 0);
						} else {
							consumedBasketItem = copyBasketItem(basketItem);
							unitsHolder.set(key, unitsToPurchase - basketItem.quantity);
							basketItem.quantity = 0;
						}

						if (Boolean.TRUE.equals(allowedBasketItem.purchaseReuse)) {
							for (String otherKey : Arrays.asList("units_to_purchase", "units_to_purchase2",
									"units_to_purchase3")) {
								Integer otherValue = unitsHolder.get(otherKey);
								if (!otherKey.equals(key) && otherValue != null && otherValue > 0) {
									BasketItem allowedBasketItemOther = allowedBasketItemsIncludes(allowedBasketItems,
											consumedBasketItem, otherKey);
									if (allowedBasketItemOther != null && allowedBasketItemOther.quantity > 0) {
										int reduceQuantityOther = Math.min(otherValue, consumedBasketItem.quantity);
										reduceQuantityOther = Math.min(otherValue, reduceQuantityOther);
										unitsHolder.set(otherKey, otherValue - reduceQuantityOther);
									}
								}
							}
						}
					}
				}
			}

			if (consumedBasketItem != null && consumedBasketItem.quantity > 0) {
				consumedBasket.add(consumedBasketItem);
			}
			if (basketItem.quantity > 0) {
				newBasket.add(basketItem);
			}
		}

		ReduceBasketResult result = new ReduceBasketResult();
		result.consumedBasket = consumedBasket;
		result.newBasket = newBasket;
		return result;
	}

	public static BasketItem allowedBasketItemsIncludes(List<BasketItem> allowedBasketItems, BasketItem item,
			String keyUnitsToPurchase) {
		for (BasketItem allowedBasketItem : allowedBasketItems) {
			if ("units_to_purchase".equals(keyUnitsToPurchase) && allowedBasketItem.purchaseType != null) {
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
			if (Objects.equals(allowedBasketItem.productCode, item.productCode)) {
				return allowedBasketItem;
			}
		}
		return null;
	}

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
