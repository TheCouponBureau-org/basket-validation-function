package org.thecouponbureau.validate.basket.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Purchase;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Range;

/**
 * =====================================================
 * BasketHelper
 * =====================================================
 *
 * Utility class for:
 * - Basket transformations
 * - Filtering eligible items
 * - Price & unit calculations
 * - Copying and grouping helpers
 */
public class BasketHelper {

	// =====================================================
	// Merge duplicate items (same productCode + price)
	// =====================================================
	public static List<BasketItem> mergeBasketItems(List<BasketItem> basket) {

		// Keeps insertion order
		Map<String, BasketItem> mergedBasket = new LinkedHashMap<>();

		for (BasketItem item : basket) {

			// Unique key: product + rounded price
			String key = item.productCode + "-" +
					String.format(Locale.US, "%.2f", item.price);

			if (mergedBasket.containsKey(key)) {
				// If already exists → increase quantity
				mergedBasket.get(key).quantity += item.quantity;

			} else {
				// Create new item copy
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

	// =====================================================
	// Reorder sub-basket to match original basket order
	// =====================================================
	public static List<BasketItem> reorderSubBasket(
			List<BasketItem> mainBasket,
			List<BasketItem> subBasket) {

		// Map productCode → original index
		Map<String, Integer> orderMap = new HashMap<>();

		for (int i = 0; i < mainBasket.size(); i++) {
			orderMap.put(mainBasket.get(i).productCode, i);
		}

		// Sort based on original order
		subBasket.sort(Comparator.comparingInt(
				item -> orderMap.getOrDefault(item.productCode, Integer.MAX_VALUE)));

		return subBasket;
	}

	// =====================================================
	// Count total units in basket
	// =====================================================
	public static int basketUnits(List<BasketItem> basket) {
		int total = 0;

		for (BasketItem item : basket) {
			total += item.quantity;
		}

		return total;
	}

	// =====================================================
	// Group product codes by product type
	// =====================================================
	public static Map<String, List<String>> getProductCodes(
			List<BasketItem> basketItems) {

		Map<String, List<String>> productCodes = new HashMap<>();

		for (BasketItem item : basketItems) {

			// Default group → GTINs
			if (item.productType == null) {
				productCodes
				.computeIfAbsent("gtins", k -> new ArrayList<>())
				.add(item.productCode);

			} else {
				productCodes
				.computeIfAbsent(item.productType, k -> new ArrayList<>())
				.add(item.productCode);
			}
		}

		return productCodes;
	}

	// =====================================================
	// Filter basket based on purchase rules
	// =====================================================
	public static List<BasketItem> allowedBasket(
			List<BasketItem> basket,
			Purchase purchase) {

		List<BasketItem> allowedBasketItems = new ArrayList<>();

		for (BasketItem item : basket) {

			boolean hasPrefixed =
					purchase.prefixedCode != null && !purchase.prefixedCode.isEmpty();

			boolean inGtins =
					purchase.gtins != null && purchase.gtins.contains(item.productCode);

			boolean inEans =
					purchase.eans != null && purchase.eans.contains(item.productCode);

			/**
			 * IMPORTANT RULE:
			 * If item is not explicitly included AND no prefix rule exists → reject
			 */
			if (!inGtins && !inEans && !hasPrefixed) {
				continue;
			}

			// Prefix inclusion range
			Range range = hasPrefixed
					? purchase.prefixedCode.get(item.productType)
							: null;

			// Prefix exclusion range
			Range excludedRange =
					purchase.excludedPrefixedCode != null
					? purchase.excludedPrefixedCode.get(item.productType)
							: null;

			// Exclude if within excluded prefix range
			if (excludedRange != null &&
					compareStringNumbers(item.productCode, excludedRange.start) >= 0 &&
					compareStringNumbers(item.productCode, excludedRange.end) <= 0) {
				continue;
			}

			// Exclude if explicitly listed
			if ((purchase.excludedGtins != null &&
					purchase.excludedGtins.contains(item.productCode))
					|| (purchase.excludedEans != null &&
					purchase.excludedEans.contains(item.productCode))) {
				continue;
			}

			// Validate prefix range inclusion
			if (range != null &&
					(compareStringNumbers(item.productCode, range.start) < 0 ||
							compareStringNumbers(item.productCode, range.end) > 0)) {
				continue;
			}

			allowedBasketItems.add(item);
		}

		return allowedBasketItems;
	}

	// =====================================================
	// Convert price to cents (avoids floating precision issues)
	// =====================================================
	public static long toCents(double price) {
		return Math.round(price * 100);
	}

	// =====================================================
	// Round double to 2 decimal places
	// =====================================================
	public static double roundTo2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	// =====================================================
	// Calculate total basket value in cents
	// =====================================================
	public static long basketValue(List<BasketItem> basket) {

		long totalValue = 0;

		for (BasketItem item : basket) {
			totalValue += toCents(item.price) * item.quantity;
		}

		return totalValue;
	}

	// =====================================================
	// Compare numeric strings safely
	// (handles GTIN/EAN numeric comparisons)
	// =====================================================
	public static int compareStringNumbers(String a, String b) {
		try {
			return Long.compare(Long.parseLong(a), Long.parseLong(b));
		} catch (Exception e) {
			// fallback for non-numeric strings
			return a.compareTo(b);
		}
	}

	// =====================================================
	// Deep copy of BasketItem
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

	// =====================================================
	// Deep copy of basket list
	// =====================================================
	public static List<BasketItem> copyBasketList(List<BasketItem> items) {

		List<BasketItem> out = new ArrayList<>();

		for (BasketItem item : items) {
			out.add(copyBasketItem(item));
		}

		return out;
	}

	// =====================================================
	// Simple status wrapper
	// =====================================================
	public static class Status {
		public boolean status;

		public Status(boolean status) {
			this.status = status;
		}
	}
	// =====================================================
	// Expands grouped items
	// =====================================================
	public static List<BasketItem> expandToUnitLevel(List<BasketItem> basket) {
		List<BasketItem> expanded = new ArrayList<>();

		// Iterate through each grouped basket item
		for (BasketItem item : basket) {

			// Expand each item into 'quantity' number of unit-level entries
			for (int i = 0; i < item.quantity; i++) {

				// Create a deep copy to avoid mutating original basket data
				BasketItem copy = copyBasketItem(item);

				// Normalize quantity to 1 for unit-level processing
				copy.quantity = 1;

				// Preserve original ordering by adding sequentially
				expanded.add(copy);
			}
		}

		return expanded;
	}
}