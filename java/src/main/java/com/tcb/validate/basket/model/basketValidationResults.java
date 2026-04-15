package com.tcb.validate.basket.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class basketValidationResults {

	// =========================
	// Models
	// =========================
	public static class BasketValidationInput {
		public List<BasketItem> basket;
		public List<Coupon> coupons;
	}

	public static class ValidationResult {
		public BasketValidationOutput basketValidationOutput;
		public boolean notAllCouponsConsumed;
	}

	public static class BasketValidationOutput {
		public long discountInCents;
		public List<AppliedCoupon> appliedCoupons;
	}

	public static class AppliedCoupon {
		public String couponCode;
		public long faceValueInCents;
		public Map<String, List<String>> productCodes;
	}

	public static class BasketItem {
		public String productCode;
		public double price;
		public int quantity;
		public String unit;
		public String productType;
		public String purchaseType;
		public Boolean purchaseReuse;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Coupon {
		public String gs1;
		public String baseGs1;
		public PurchaseRequirement purchaseRequirement;
	}

	public static class PurchaseRequirement {
		public Integer saveValueCode;
		public Integer appliesToWhichItem;
		public Integer additionalPurchaseRulesCode;

		public Long primaryPurchaseSaveValue;
		public Long primaryPurchaseRequirements;
		public Integer primaryPurchaseReqCode;
		public List<String> primaryPurchaseGtins;
		public List<String> primaryPurchaseEans;
		public List<String> excludedPrimaryPurchaseGtins;
		public List<String> excludedPrimaryPurchaseEans;
		public Object primaryPurchasePrefixedCode;
		public Object excludedPrimaryPurchasePrefixedCode;

		public Long secondPurchaseSaveValue;
		public Long secondPurchaseRequirements;
		public Integer secondPurchaseReqCode;
		public List<String> secondPurchaseGtins;
		public List<String> secondPurchaseEans;
		public List<String> excludedSecondPurchaseGtins;
		public List<String> excludedSecondPurchaseEans;
		public Object secondPurchasePrefixedCode;
		public Object excludedSecondPurchasePrefixedCode;

		public Long thirdPurchaseSaveValue;
		public Long thirdPurchaseRequirements;
		public Integer thirdPurchaseReqCode;
		public List<String> thirdPurchaseGtins;
		public List<String> thirdPurchaseEans;
		public List<String> excludedThirdPurchaseGtins;
		public List<String> excludedThirdPurchaseEans;
		public Object thirdPurchasePrefixedCode;
		public Object excludedThirdPurchasePrefixedCode;
	}

	public static class Purchase {
		public Long saveValue;
		public Long requirements;
		public Integer reqCode;
		public List<String> gtins;
		public List<String> excludedGtins;
		public List<String> eans;
		public List<String> excludedEans;
		public Map<String, Range> prefixedCode = new HashMap<>();
		public Map<String, Range> excludedPrefixedCode = new HashMap<>();
	}

	public static class Purchases {
		public Purchase primaryPurchase;
		public Purchase secondPurchase;
		public Purchase thirdPurchase;
	}

	public static class Range {
		public String start;
		public String end;

		public Range() {
		}

		public Range(String start, String end) {
			this.start = start;
			this.end = end;
		}
	}

	public static class Status {
		public boolean status;

		public Status(boolean status) {
			this.status = status;
		}
	}

	public static class BasketHasUnitsResult {
		public boolean status;
		public List<BasketItem> basketItems;
	}

	public static class MeetsPurchaseRequirementsResult {
		public boolean status;
		public List<BasketItem> basketItems;
		public Integer unitsToPurchase;

		public MeetsPurchaseRequirementsResult(boolean status, List<BasketItem> basketItems, Integer unitsToPurchase) {
			this.status = status;
			this.basketItems = basketItems;
			this.unitsToPurchase = unitsToPurchase;
		}
	}

	public static class MeetsRequirementsResult {
		public boolean status;
		public List<BasketItem> basketItems = new ArrayList<>();
		public Integer unitsToPurchase;
		public Integer unitsToPurchase2;
		public Integer unitsToPurchase3;

		public MeetsRequirementsResult() {
		}

		public MeetsRequirementsResult(boolean status, List<BasketItem> basketItems, Integer unitsToPurchase,
				Integer unitsToPurchase2, Integer unitsToPurchase3) {
			this.status = status;
			this.basketItems = basketItems != null ? basketItems : new ArrayList<>();
			this.unitsToPurchase = unitsToPurchase;
			this.unitsToPurchase2 = unitsToPurchase2;
			this.unitsToPurchase3 = unitsToPurchase3;
		}

		public static MeetsRequirementsResult negative() {
			MeetsRequirementsResult r = new MeetsRequirementsResult();
			r.status = false;
			r.basketItems = new ArrayList<>();
			return r;
		}
	}

	public static class ReduceBasketResult {
		public List<BasketItem> consumedBasket;
		public List<BasketItem> newBasket;
	}

	public static class UnitsToPurchaseHolder {
		private Integer unitsToPurchase;
		private Integer unitsToPurchase2;
		private Integer unitsToPurchase3;

		public UnitsToPurchaseHolder(Integer unitsToPurchase, Integer unitsToPurchase2, Integer unitsToPurchase3) {
			this.unitsToPurchase = unitsToPurchase;
			this.unitsToPurchase2 = unitsToPurchase2;
			this.unitsToPurchase3 = unitsToPurchase3;
		}

		public Integer get(String key) {
			switch (key) {
			case "units_to_purchase":
				return unitsToPurchase;
			case "units_to_purchase2":
				return unitsToPurchase2;
			case "units_to_purchase3":
				return unitsToPurchase3;
			default:
				return null;
			}
		}

		public void set(String key, Integer value) {
			switch (key) {
			case "units_to_purchase":
				unitsToPurchase = value;
				break;
			case "units_to_purchase2":
				unitsToPurchase2 = value;
				break;
			case "units_to_purchase3":
				unitsToPurchase3 = value;
				break;
			}
		}
	}
}
