package com.tcb.validate.basket.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.tcb.validate.basket.factory.PurchaseFactory;
import com.tcb.validate.basket.helper.BasketHelper;
import com.tcb.validate.basket.model.basketValidationResults.BasketHasUnitsResult;
import com.tcb.validate.basket.model.basketValidationResults.BasketItem;
import com.tcb.validate.basket.model.basketValidationResults.Coupon;
import com.tcb.validate.basket.model.basketValidationResults.MeetsPurchaseRequirementsResult;
import com.tcb.validate.basket.model.basketValidationResults.MeetsRequirementsResult;
import com.tcb.validate.basket.model.basketValidationResults.Purchase;
import com.tcb.validate.basket.model.basketValidationResults.PurchaseRequirement;
import com.tcb.validate.basket.model.basketValidationResults.Purchases;

public class RequirementService {

	// =========================
	// Requirement evaluation
	// =========================
	public static MeetsRequirementsResult meetsRequirements(List<BasketItem> basket, Coupon coupon) {
		if (coupon.purchaseRequirement == null) {
			System.err.println("Coupon does not have purchase requirement");
			return MeetsRequirementsResult.negative();
		}

		PurchaseRequirement pr = coupon.purchaseRequirement;
		Integer additionalPurchaseRulesCode = pr.additionalPurchaseRulesCode;
		Integer appliesToWhichItem = pr.appliesToWhichItem;

		Purchases purchasesObj = PurchaseFactory.getPurchases(pr);
		Purchase primaryPurchase = purchasesObj.primaryPurchase;
		Purchase secondPurchase = purchasesObj.secondPurchase;
		Purchase thirdPurchase = purchasesObj.thirdPurchase;

		if (additionalPurchaseRulesCode == null) {
			MeetsPurchaseRequirementsResult result = meetsPurchaseRequirements(coupon, basket, primaryPurchase, true);
			return new MeetsRequirementsResult(result.status, result.basketItems, result.unitsToPurchase, null, null);

		} else if (additionalPurchaseRulesCode == 0) {
			List<Purchase> purchases = Arrays.asList(primaryPurchase, secondPurchase, thirdPurchase);
			List<String> purchaseTypes = Arrays.asList("", "second_purchase", "third_purchase");

			if (appliesToWhichItem == null) {
				for (BasketItem basketItem : basket) {
					for (int i = 0; i < purchases.size(); i++) {
						Purchase purchase = purchases.get(i);
						if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {
							MeetsPurchaseRequirementsResult result = meetsPurchaseRequirements(coupon, basket, purchase,
									true);

							boolean basketItemFound = result.basketItems.stream()
									.anyMatch(item -> Objects.equals(item.productCode, basketItem.productCode));

							if (result.status && basketItemFound) {
								long totalValue = BasketHelper.basketValue(result.basketItems);
								long primarySaveValue = primaryPurchase.saveValue != null ? primaryPurchase.saveValue
										: 0;

								if (totalValue >= primarySaveValue) {
									List<BasketItem> basketItems = BasketHelper.copyBasketList(result.basketItems);
									if (i > 0) {
										for (BasketItem item : basketItems) {
											item.purchaseType = purchaseTypes.get(i);
										}
									}

									MeetsRequirementsResult out = new MeetsRequirementsResult();
									out.status = true;
									out.basketItems = basketItems;
									if (i == 0)
										out.unitsToPurchase = result.unitsToPurchase;
									if (i == 1)
										out.unitsToPurchase2 = result.unitsToPurchase;
									if (i == 2)
										out.unitsToPurchase3 = result.unitsToPurchase;
									return out;
								}
							}
						}
					}
				}
			} else if (appliesToWhichItem >= 0 && appliesToWhichItem <= 2) {
				Purchase purchase = purchases.get(appliesToWhichItem);
				if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {
					MeetsPurchaseRequirementsResult result = meetsPurchaseRequirements(coupon, basket, purchase, true);
					if (result.status) {
						List<BasketItem> basketItems = BasketHelper.copyBasketList(result.basketItems);
						if (appliesToWhichItem > 0) {
							for (BasketItem item : basketItems) {
								item.purchaseType = purchaseTypes.get(appliesToWhichItem);
							}
						}

						MeetsRequirementsResult out = new MeetsRequirementsResult();
						out.status = true;
						out.basketItems = basketItems;
						if (appliesToWhichItem == 0)
							out.unitsToPurchase = result.unitsToPurchase;
						if (appliesToWhichItem == 1)
							out.unitsToPurchase2 = result.unitsToPurchase;
						if (appliesToWhichItem == 2)
							out.unitsToPurchase3 = result.unitsToPurchase;
						return out;
					}
				}
			}

		} else if (additionalPurchaseRulesCode == 1) {
			MeetsPurchaseRequirementsResult r1 = meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);
			if (!r1.status)
				return MeetsRequirementsResult.negative();

			List<BasketItem> basketItems1 = r1.basketItems;
			Integer unitsToPurchase1 = r1.unitsToPurchase;

			List<BasketItem> basketItems2 = null;
			Integer unitsToPurchase2 = null;
			if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {
				MeetsPurchaseRequirementsResult r2 = meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
				if (!r2.status)
					return MeetsRequirementsResult.negative();
				basketItems2 = r2.basketItems;
				unitsToPurchase2 = r2.unitsToPurchase;
			}

			List<BasketItem> basketItems3 = null;
			Integer unitsToPurchase3 = null;
			if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {
				MeetsPurchaseRequirementsResult r3 = meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);
				if (!r3.status)
					return MeetsRequirementsResult.negative();
				basketItems3 = r3.basketItems;
				unitsToPurchase3 = r3.unitsToPurchase;
			}

			if (basketItems2 != null) {
				for (BasketItem item : basketItems2)
					item.purchaseType = "second_purchase";
			}
			if (basketItems3 != null) {
				for (BasketItem item : basketItems3)
					item.purchaseType = "third_purchase";
			}

			List<BasketItem> combined = new ArrayList<>();
			combined.addAll(basketItems1);
			if (basketItems2 != null)
				combined.addAll(basketItems2);
			if (basketItems3 != null)
				combined.addAll(basketItems3);

			List<BasketItem> basketItemsFinal = BasketHelper.reorderSubBasket(basket, combined);

			return new MeetsRequirementsResult(true, basketItemsFinal, unitsToPurchase1, unitsToPurchase2,
					unitsToPurchase3);

		} else if (additionalPurchaseRulesCode == 2) {
			MeetsPurchaseRequirementsResult r1 = meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);
			if (!r1.status)
				return MeetsRequirementsResult.negative();

			List<BasketItem> basketItems1 = r1.basketItems;
			Integer unitsToPurchase1 = r1.unitsToPurchase;

			boolean status2 = false;
			List<BasketItem> basketItems2 = null;
			Integer unitsToPurchase2 = null;
			if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {
				MeetsPurchaseRequirementsResult r2 = meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
				status2 = r2.status;
				basketItems2 = r2.basketItems;
				unitsToPurchase2 = r2.unitsToPurchase;
			}

			boolean status3 = false;
			List<BasketItem> basketItems3 = null;
			Integer unitsToPurchase3 = null;
			if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {
				MeetsPurchaseRequirementsResult r3 = meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);
				status3 = r3.status;
				basketItems3 = r3.basketItems;
				unitsToPurchase3 = r3.unitsToPurchase;
			}

			if (status2 && basketItems2 != null) {
				for (BasketItem item : basketItems2)
					item.purchaseType = "second_purchase";
			} else if (status3 && basketItems3 != null) {
				for (BasketItem item : basketItems3)
					item.purchaseType = "third_purchase";
			} else {
				return MeetsRequirementsResult.negative();
			}

			List<BasketItem> combined = new ArrayList<>();
			combined.addAll(basketItems1);
			if (status2 && basketItems2 != null)
				combined.addAll(basketItems2);
			else if (status3 && basketItems3 != null)
				combined.addAll(basketItems3);

			List<BasketItem> basketItemsFinal = BasketHelper.reorderSubBasket(basket, combined);

			return new MeetsRequirementsResult(true, basketItemsFinal, unitsToPurchase1, unitsToPurchase2,
					unitsToPurchase3);
		}

		return MeetsRequirementsResult.negative();
	}

	public static MeetsPurchaseRequirementsResult meetsPurchaseRequirements(Coupon coupon, List<BasketItem> basket,
			Purchase purchase, boolean applyAdditionalUnits) {
		int unitsToPurchase = 0;

		if (purchase.reqCode != null && purchase.reqCode == 0) {
			unitsToPurchase = purchase.requirements != null ? purchase.requirements.intValue() : 0;

			// ✅ STEP 1: filter only eligible items
			List<BasketItem> eligibleBasket = BasketHelper.allowedBasket(basket, purchase);

			// ❗ IMPORTANT: if no eligible items → FAIL immediately
			if (eligibleBasket == null || eligibleBasket.isEmpty()) {
				return new MeetsPurchaseRequirementsResult(false, new ArrayList<>(), 0);
			}

			// ✅ STEP 2: pass ONLY filtered basket
			BasketHasUnitsResult result = basketHasUnitsToPurchase(eligibleBasket, unitsToPurchase, purchase);

			if (applyAdditionalUnits) {
				unitsToPurchase += getAdditionalUnitsToPurchase(coupon, result.basketItems, unitsToPurchase, purchase);
			}

			return new MeetsPurchaseRequirementsResult(result.status, result.basketItems, unitsToPurchase);

		} else if (purchase.reqCode != null && purchase.reqCode == 1) {
			long cashValueTotalTransaction = 0;
			int unitCount = 0;

			List<BasketItem> newBasket = BasketHelper.allowedBasket(basket, purchase);

			for (BasketItem item : newBasket) {
				for (int i = 0; i < item.quantity; i++) {
					if (cashValueTotalTransaction < purchase.requirements) {
						cashValueTotalTransaction += BasketHelper.toCents(item.price);
						unitCount++;
					}
				}
			}

			boolean status = cashValueTotalTransaction >= purchase.requirements;
			return new MeetsPurchaseRequirementsResult(status, newBasket, unitCount);

		} else if (purchase.reqCode != null && purchase.reqCode == 2) {
			long cashValueTotalTransaction = 0;
			int unitCount = 0;

			List<BasketItem> updatedBasket = new ArrayList<>();
			for (BasketItem item : basket) {
				BasketItem copied = BasketHelper.copyBasketItem(item);
				for (int i = 0; i < item.quantity; i++) {
					if (cashValueTotalTransaction < purchase.requirements) {
						cashValueTotalTransaction += BasketHelper.toCents(item.price);
						unitCount++;
					}
				}
				copied.purchaseReuse = true;
				updatedBasket.add(copied);
			}

			boolean status = cashValueTotalTransaction >= purchase.requirements;
			return new MeetsPurchaseRequirementsResult(status, updatedBasket, unitCount);
		}

		return new MeetsPurchaseRequirementsResult(false, new ArrayList<>(), 0);
	}

	public static BasketHasUnitsResult basketHasUnitsToPurchase(List<BasketItem> basket, int unitsToPurchase,
			Purchase purchase) {
		List<BasketItem> allowedBasketItems = BasketHelper.allowedBasket(basket, purchase);
		int unitsPurchased = 0;
		for (BasketItem item : allowedBasketItems) {
			unitsPurchased += item.quantity;
		}

		BasketHasUnitsResult result = new BasketHasUnitsResult();
		result.status = unitsPurchased >= unitsToPurchase;
		result.basketItems = allowedBasketItems;
		return result;
	}

	public static int getAdditionalUnitsToPurchase(Coupon coupon, List<BasketItem> basketItems, int unitsToPurchase,
			Purchase purchase) {
		if (purchase.reqCode != null && purchase.reqCode == 0) {
			long totalPriceUnitsToPurchase = 0;
			int count = 0;
			int additionalUnitsToPurchase = 0;

			for (BasketItem item : basketItems) {
				for (int i = 0; i < item.quantity; i++) {
					if (count < unitsToPurchase) {
						totalPriceUnitsToPurchase += BasketHelper.toCents(item.price);
					} else {
						long requiredSaveValue = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
								? coupon.purchaseRequirement.primaryPurchaseSaveValue
								: 0;

						if (requiredSaveValue > totalPriceUnitsToPurchase) {
							additionalUnitsToPurchase++;
							totalPriceUnitsToPurchase += BasketHelper.toCents(item.price);
						}
					}
					count++;
				}
			}

			return additionalUnitsToPurchase;
		}

		return 0;
	}
}
