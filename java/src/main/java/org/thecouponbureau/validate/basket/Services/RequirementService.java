package org.thecouponbureau.validate.basket.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.thecouponbureau.validate.basket.factory.PurchaseFactory;
import org.thecouponbureau.validate.basket.helper.BasketHelper;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketHasUnitsResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.MeetsPurchaseRequirementsResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.MeetsRequirementsResult;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Purchase;
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Purchases;

public class RequirementService {

    // =====================================================
    // Main entry: Checks if basket satisfies coupon requirements
    // =====================================================
    public static MeetsRequirementsResult meetsRequirements(
            List<BasketItem> basket,
            Coupon coupon) {

        // ❗ Coupon must have purchase rules
        if (coupon.purchaseRequirement == null) {
            System.err.println("Coupon does not have purchase requirement");
            return MeetsRequirementsResult.negative();
        }

        PurchaseRequirement pr = coupon.purchaseRequirement;

        // Defines rule behavior (AND / OR / SINGLE)
        Integer additionalPurchaseRulesCode = pr.additionalPurchaseRulesCode;

        // Restricts which purchase group to apply (primary/secondary/third)
        Integer appliesToWhichItem = pr.appliesToWhichItem;

        // Build purchase objects (primary, second, third)
        Purchases purchasesObj = PurchaseFactory.getPurchases(pr);
        Purchase primaryPurchase = purchasesObj.primaryPurchase;
        Purchase secondPurchase = purchasesObj.secondPurchase;
        Purchase thirdPurchase = purchasesObj.thirdPurchase;

        // =====================================================
        // CASE: No additional rules → only primary purchase
        // =====================================================
        if (additionalPurchaseRulesCode == null) {

            MeetsPurchaseRequirementsResult result =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, true);

            return new MeetsRequirementsResult(
                    result.status,
                    result.basketItems,
                    result.unitsToPurchase,
                    null,
                    null
            );

        // =====================================================
        // CASE 0 → ANY purchase condition can satisfy (OR logic)
        // =====================================================
        } else if (additionalPurchaseRulesCode == 0) {

            List<Purchase> purchases =
                    Arrays.asList(primaryPurchase, secondPurchase, thirdPurchase);

            List<String> purchaseTypes =
                    Arrays.asList("", "second_purchase", "third_purchase");

            // No restriction → try all purchase types
            if (appliesToWhichItem == null) {

                for (BasketItem basketItem : basket) {
                    for (int i = 0; i < purchases.size(); i++) {

                        Purchase purchase = purchases.get(i);

                        if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {

                            MeetsPurchaseRequirementsResult result =
                                    meetsPurchaseRequirements(coupon, basket, purchase, true);

                            // Ensure current basket item is part of qualifying set
                            boolean basketItemFound =
                                    result.basketItems.stream()
                                            .anyMatch(item ->
                                                    Objects.equals(item.productCode, basketItem.productCode));

                            if (result.status && basketItemFound) {

                                long totalValue =
                                        BasketHelper.basketValue(result.basketItems);

                                long primarySaveValue =
                                        primaryPurchase.saveValue != null
                                                ? primaryPurchase.saveValue
                                                : 0;

                                // Ensure minimum spend condition
                                if (totalValue >= primarySaveValue) {

                                    List<BasketItem> basketItems =
                                            BasketHelper.copyBasketList(result.basketItems);

                                    // Assign purchase type if secondary/third
                                    if (i > 0) {
                                        for (BasketItem item : basketItems) {
                                            item.purchaseType = purchaseTypes.get(i);
                                        }
                                    }

                                    MeetsRequirementsResult out =
                                            new MeetsRequirementsResult();

                                    out.status = true;
                                    out.basketItems = basketItems;

                                    // Assign units based on purchase level
                                    if (i == 0) out.unitsToPurchase = result.unitsToPurchase;
                                    if (i == 1) out.unitsToPurchase2 = result.unitsToPurchase;
                                    if (i == 2) out.unitsToPurchase3 = result.unitsToPurchase;

                                    return out;
                                }
                            }
                        }
                    }
                }

            // If explicitly restricted to one purchase type
            } else if (appliesToWhichItem >= 0 && appliesToWhichItem <= 2) {

                Purchase purchase = purchases.get(appliesToWhichItem);

                if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {

                    MeetsPurchaseRequirementsResult result =
                            meetsPurchaseRequirements(coupon, basket, purchase, true);

                    if (result.status) {

                        List<BasketItem> basketItems =
                                BasketHelper.copyBasketList(result.basketItems);

                        if (appliesToWhichItem > 0) {
                            for (BasketItem item : basketItems) {
                                item.purchaseType =
                                        purchaseTypes.get(appliesToWhichItem);
                            }
                        }

                        MeetsRequirementsResult out =
                                new MeetsRequirementsResult();

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

        // =====================================================
        // CASE 1 → ALL purchase conditions must be satisfied (AND)
        // =====================================================
        } else if (additionalPurchaseRulesCode == 1) {

            // Validate primary purchase
            MeetsPurchaseRequirementsResult r1 =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);

            if (!r1.status) return MeetsRequirementsResult.negative();

            //List<BasketItem> basketItems1 = r1.basketItems;
            List<BasketItem> basketItems1 =
            	    BasketHelper.copyBasketList(r1.basketItems);
           
            
            
            
            Integer unitsToPurchase1 = r1.unitsToPurchase;

            // Validate second purchase (if exists)
            List<BasketItem> basketItems2 = null;
            Integer unitsToPurchase2 = null;

            if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {

                MeetsPurchaseRequirementsResult r2 =
                        meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
                
                if (!r2.status) return MeetsRequirementsResult.negative();

                basketItems2 = BasketHelper.copyBasketList(r2.basketItems);
                
                unitsToPurchase2 = r2.unitsToPurchase;
                
            }

            // Validate third purchase (if exists)
            List<BasketItem> basketItems3 = null;
            Integer unitsToPurchase3 = null;

            if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {

                MeetsPurchaseRequirementsResult r3 =
                        meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);

                if (!r3.status) return MeetsRequirementsResult.negative();

                basketItems3 = BasketHelper.copyBasketList(r3.basketItems);
                unitsToPurchase3 = r3.unitsToPurchase;
            }

            // Assign purchase types
            if (basketItems2 != null)
                for (BasketItem item : basketItems2)
                    item.purchaseType = "second_purchase";

            if (basketItems3 != null)
                for (BasketItem item : basketItems3)
                    item.purchaseType = "third_purchase";

            // Combine all qualifying items
            List<BasketItem> combined = new ArrayList<>();
            combined.addAll(basketItems1);
            if (basketItems2 != null) combined.addAll(basketItems2);
            if (basketItems3 != null) combined.addAll(basketItems3);

            // Preserve original order
            List<BasketItem> basketItemsFinal =
                    BasketHelper.reorderSubBasket(basket, combined);

            return new MeetsRequirementsResult(
                    true,
                    basketItemsFinal,
                    unitsToPurchase1,
                    unitsToPurchase2,
                    unitsToPurchase3
            );

        // =====================================================
        // CASE 2 → Primary AND (Secondary OR Third)
        // =====================================================
        } else if (additionalPurchaseRulesCode == 2) {

            MeetsPurchaseRequirementsResult r1 =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);

            if (!r1.status) return MeetsRequirementsResult.negative();

            List<BasketItem> basketItems1 = r1.basketItems;
            Integer unitsToPurchase1 = r1.unitsToPurchase;

            boolean status2 = false;
            boolean status3 = false;

            List<BasketItem> basketItems2 = null;
            List<BasketItem> basketItems3 = null;

            Integer unitsToPurchase2 = null;
            Integer unitsToPurchase3 = null;

            if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r2 =
                        meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
                status2 = r2.status;
                basketItems2 = r2.basketItems;
                unitsToPurchase2 = r2.unitsToPurchase;
            }

            if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r3 =
                        meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);
                status3 = r3.status;
                basketItems3 = r3.basketItems;
                unitsToPurchase3 = r3.unitsToPurchase;
            }

            // Require at least one of secondary or third
            if (status2 && basketItems2 != null) {
                for (BasketItem item : basketItems2)
                    item.purchaseType = "second_purchase";

            } else if (status3 && basketItems3 != null) {
                for (BasketItem item : basketItems3)
                    item.purchaseType = "third_purchase";

            } else {
                return MeetsRequirementsResult.negative();
            }

            // Combine
            List<BasketItem> combined = new ArrayList<>();
            combined.addAll(basketItems1);

            if (status2 && basketItems2 != null)
                combined.addAll(basketItems2);
            else if (status3 && basketItems3 != null)
                combined.addAll(basketItems3);

            List<BasketItem> basketItemsFinal =
                    BasketHelper.reorderSubBasket(basket, combined);

            return new MeetsRequirementsResult(
                    true,
                    basketItemsFinal,
                    unitsToPurchase1,
                    unitsToPurchase2,
                    unitsToPurchase3
            );
        }

        return MeetsRequirementsResult.negative();
    }

    // =====================================================
    // Evaluates a single purchase condition
    // =====================================================
    public static MeetsPurchaseRequirementsResult meetsPurchaseRequirements(
            Coupon coupon,
            List<BasketItem> basket,
            Purchase purchase,
            boolean applyAdditionalUnits) {

        int unitsToPurchase = 0;

        // =====================================================
        // reqCode 0 → Unit-based requirement
        // =====================================================
        if (purchase.reqCode != null && purchase.reqCode == 0) {

            unitsToPurchase =
                    purchase.requirements != null
                            ? purchase.requirements.intValue()
                            : 0;

            // Filter eligible items
            List<BasketItem> eligibleBasket =
                    BasketHelper.allowedBasket(basket, purchase);
            eligibleBasket = BasketHelper.expandToUnitLevel(eligibleBasket);

            if (eligibleBasket == null || eligibleBasket.isEmpty()) {
                return new MeetsPurchaseRequirementsResult(false, new ArrayList<>(), 0);
            }

            // Check unit availability
            BasketHasUnitsResult result =
                    basketHasUnitsToPurchase(eligibleBasket, unitsToPurchase, purchase);

            // Add extra units if needed to meet save value condition
            if (applyAdditionalUnits) {
                unitsToPurchase += getAdditionalUnitsToPurchase(
                        coupon,
                        result.basketItems,
                        unitsToPurchase,
                        purchase
                );
            }

            return new MeetsPurchaseRequirementsResult(
                    result.status,
                    result.basketItems,
                    unitsToPurchase
            );

        // =====================================================
        // reqCode 1 → Spend-based requirement (filtered basket)
        // =====================================================
        } else if (purchase.reqCode != null && purchase.reqCode == 1) {

            long total = 0;
            int unitCount = 0;

            List<BasketItem> newBasket =
                    BasketHelper.allowedBasket(basket, purchase);

            for (BasketItem item : newBasket) {
                for (int i = 0; i < item.quantity; i++) {
                    if (total < purchase.requirements) {
                        total += BasketHelper.toCents(item.price);
                        unitCount++;
                    }
                }
            }

            boolean status = total >= purchase.requirements;

            return new MeetsPurchaseRequirementsResult(
                    status,
                    newBasket,
                    unitCount
            );

        // =====================================================
        // reqCode 2 → Spend-based (ALL items, reusable)
        // =====================================================
        } else if (purchase.reqCode != null && purchase.reqCode == 2) {

            long total = 0;
            int unitCount = 0;

            List<BasketItem> updatedBasket = new ArrayList<>();

            for (BasketItem item : basket) {

                BasketItem copied =
                        BasketHelper.copyBasketItem(item);

                for (int i = 0; i < item.quantity; i++) {
                    if (total < purchase.requirements) {
                        total += BasketHelper.toCents(item.price);
                        unitCount++;
                    }
                }

                // Mark reusable for other conditions
                copied.purchaseReuse = true;

                updatedBasket.add(copied);
            }

            boolean status = total >= purchase.requirements;

            return new MeetsPurchaseRequirementsResult(
                    status,
                    updatedBasket,
                    unitCount
            );
        }

        return new MeetsPurchaseRequirementsResult(false, new ArrayList<>(), 0);
    }

    // =====================================================
    // Checks if basket has enough units
    // =====================================================
    public static BasketHasUnitsResult basketHasUnitsToPurchase(
            List<BasketItem> basket,
            int unitsToPurchase,
            Purchase purchase) {

        List<BasketItem> allowedBasketItems =
                BasketHelper.allowedBasket(basket, purchase);

        int unitsPurchased = 0;

        for (BasketItem item : allowedBasketItems) {
            unitsPurchased += item.quantity;
        }

        BasketHasUnitsResult result = new BasketHasUnitsResult();
        result.status = unitsPurchased >= unitsToPurchase;
        result.basketItems = allowedBasketItems;

        return result;
    }

    // =====================================================
    // Calculates additional units needed to satisfy save value
    // =====================================================
    public static int getAdditionalUnitsToPurchase(
            Coupon coupon,
            List<BasketItem> basketItems,
            int unitsToPurchase,
            Purchase purchase) {

        if (purchase.reqCode != null && purchase.reqCode == 0) {

            long totalPrice = 0;
            int count = 0;
            int additionalUnits = 0;

            for (BasketItem item : basketItems) {
                for (int i = 0; i < item.quantity; i++) {

                    if (count < unitsToPurchase) {
                        totalPrice += BasketHelper.toCents(item.price);
                    } else {

                        long requiredSaveValue =
                                coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                                        ? coupon.purchaseRequirement.primaryPurchaseSaveValue
                                        : 0;

                        // If value not reached → add more units
                        if (requiredSaveValue > totalPrice) {
                            additionalUnits++;
                            totalPrice += BasketHelper.toCents(item.price);
                        }
                    }
                    count++;
                }
            }

            return additionalUnits;
        }

        return 0;
    }
}