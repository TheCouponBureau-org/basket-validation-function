package com.tcb.validate.basket.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tcb.validate.basket.model.basketValidationResults.BasketItem;
import com.tcb.validate.basket.model.basketValidationResults.Purchase;
import com.tcb.validate.basket.model.basketValidationResults.Range;

public class BasketHelper {

	// =========================
	// Basket helpers
	// =========================
	public static List<BasketItem> mergeBasketItems(List<BasketItem> basket) {
		Map<String, BasketItem> mergedBasket = new LinkedHashMap<>();

		for (BasketItem item : basket) {
			String key = item.productCode + "-" + String.format(Locale.US, "%.2f", item.price);

			if (mergedBasket.containsKey(key)) {
				mergedBasket.get(key).quantity += item.quantity;
			} else {
				BasketItem newItem = new BasketItem();
				newItem.productCode = item.productCode;
				newItem.price = roundTo2(item.price);
				newItem.quantity = item.quantity;
				newItem.unit = item.unit;
				newItem.productType = item.productType;
				newItem.purchaseType = item.purchaseType;
				newItem.purchaseReuse = item.purchaseReuse;
				mergedBasket.put(key, newItem);
			}
		}

		return new ArrayList<>(mergedBasket.values());
	}

	public static List<BasketItem> reorderSubBasket(List<BasketItem> mainBasket, List<BasketItem> subBasket) {
		Map<String, Integer> orderMap = new HashMap<>();
		for (int i = 0; i < mainBasket.size(); i++) {
			orderMap.put(mainBasket.get(i).productCode, i);
		}

		subBasket.sort(Comparator.comparingInt(item -> orderMap.getOrDefault(item.productCode, Integer.MAX_VALUE)));
		return subBasket;
	}

	public static int basketUnits(List<BasketItem> basket) {
		int total = 0;
		for (BasketItem item : basket) {
			total += item.quantity;
		}
		return total;
	}

	// =========================
	// Product code grouping
	// =========================
	public static Map<String, List<String>> getProductCodes(List<BasketItem> basketItems) {
		Map<String, List<String>> productCodes = new HashMap<>();

		for (BasketItem item : basketItems) {
			if (item.productType == null) {
				productCodes.computeIfAbsent("gtins", k -> new ArrayList<>()).add(item.productCode);
			} else {
				productCodes.computeIfAbsent(item.productType, k -> new ArrayList<>()).add(item.productCode);
			}
		}

		return productCodes;
	}

	public static List<BasketItem> allowedBasket(List<BasketItem> basket, Purchase purchase) {
		List<BasketItem> allowedBasketItems = new ArrayList<>();

		for (BasketItem item : basket) {

			boolean hasPrefixed = purchase.prefixedCode != null && !purchase.prefixedCode.isEmpty();

			boolean inGtins = purchase.gtins != null && purchase.gtins.contains(item.productCode);
			boolean inEans = purchase.eans != null && purchase.eans.contains(item.productCode);

			// ❗ FIX: do NOT allow everything when prefixedCode is null
			if (!inGtins && !inEans && !hasPrefixed) {
				continue;
			}

			Range range = hasPrefixed ? purchase.prefixedCode.get(item.productType) : null;
			Range excludedRange = purchase.excludedPrefixedCode != null
					? purchase.excludedPrefixedCode.get(item.productType)
					: null;

			if (excludedRange != null && compareStringNumbers(item.productCode, excludedRange.start) >= 0
					&& compareStringNumbers(item.productCode, excludedRange.end) <= 0) {
				continue;
			}

			if ((purchase.excludedGtins != null && purchase.excludedGtins.contains(item.productCode))
					|| (purchase.excludedEans != null && purchase.excludedEans.contains(item.productCode))) {
				continue;
			}

			if (range != null && (compareStringNumbers(item.productCode, range.start) < 0
					|| compareStringNumbers(item.productCode, range.end) > 0)) {
				continue;
			}

			allowedBasketItems.add(item);
		}

		return allowedBasketItems;
	}

	public static long toCents(double price) {
		return Math.round(price * 100);
	}

	public static double roundTo2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	public static long basketValue(List<BasketItem> basket) {
		long totalValue = 0;
		for (BasketItem item : basket) {
			totalValue += toCents(item.price) * item.quantity;
		}
		return totalValue;
	}

	public static int compareStringNumbers(String a, String b) {
		try {
			return Long.compare(Long.parseLong(a), Long.parseLong(b));
		} catch (Exception e) {
			return a.compareTo(b);
		}
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

	public static List<BasketItem> copyBasketList(List<BasketItem> items) {
		List<BasketItem> out = new ArrayList<>();
		for (BasketItem item : items) {
			out.add(copyBasketItem(item));
		}
		return out;
	}

	public static class Status {
		public boolean status;

		public Status(boolean status) {
			this.status = status;
		}
	}
}
