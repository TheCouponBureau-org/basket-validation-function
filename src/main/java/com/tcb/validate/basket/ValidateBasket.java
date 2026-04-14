package com.tcb.validate.basket;


import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class ValidateBasket {

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

        if (basketValidationInput == null
                || basketValidationInput.basket == null
                || basketValidationInput.coupons == null) {
            ValidationResult result = new ValidationResult();
            result.basketValidationOutput = defaultOutput;
            result.notAllCouponsConsumed = false;
            return result;
        }

        BasketValidationOutput basketValidationOutput = new BasketValidationOutput();
        basketValidationOutput.discountInCents = 0;
        basketValidationOutput.appliedCoupons = new ArrayList<>();

        List<BasketItem> newBasket = mergeBasketItems(basketValidationInput.basket);
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
                newBasketTotalPrice += toCents(item.price) * item.quantity;
            }

            MeetsRequirementsResult meetsResult = meetsRequirements(newBasket, coupon);

            if (meetsResult.status) {
                boolean hasOnlyPrimaryPurchase =
                        meetsResult.unitsToPurchase2 == null && meetsResult.unitsToPurchase3 == null;

                long discountInCents = getDiscountInCents(
                        coupon,
                        meetsResult.basketItems,
                        hasOnlyPrimaryPurchase,
                        newBasketTotalPrice,
                        new ArrayList<>()
                );

                if (discountInCents <= 0) {
                    continue;
                }

                int oldBasketUnits = basketUnits(newBasket);

                if (meetsResult.basketItems == null || meetsResult.basketItems.isEmpty()) {
                    continue;
                }
                
                ReduceBasketResult reducedBasket = reduceBasket(
                        newBasket,
                        meetsResult.basketItems,
                        new UnitsToPurchaseHolder(
                                meetsResult.unitsToPurchase,
                                meetsResult.unitsToPurchase2,
                                meetsResult.unitsToPurchase3
                        )
                );

                List<BasketItem> consumedBasket = reducedBasket.consumedBasket;

                discountInCents = getDiscountInCents(
                        coupon,
                        meetsResult.basketItems,
                        hasOnlyPrimaryPurchase,
                        newBasketTotalPrice,
                        consumedBasket
                );

                newBasket = reducedBasket.newBasket;

                int newBasketUnits = basketUnits(newBasket);

                AppliedCoupon appliedCoupon = new AppliedCoupon();
                appliedCoupon.couponCode = coupon.gs1;
                appliedCoupon.faceValueInCents = discountInCents;
                appliedCoupon.productCodes = getProductCodes(consumedBasket);

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

    // =========================
    // Discount calculation
    // =========================
    public static long getDiscountInCents(
            Coupon coupon,
            List<BasketItem> basketItems,
            boolean hasOnlyPrimaryPurchase,
            long newBasketTotalPrice,
            List<BasketItem> consumedBasket
    ) {
        Integer saveValueCode = coupon.purchaseRequirement.saveValueCode != null
                ? coupon.purchaseRequirement.saveValueCode : 0;

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
                    Set<String> consumedCodes = consumedBasket.stream()
                            .map(i -> i.productCode)
                            .collect(Collectors.toSet());

                    newBasketItems = newBasketItems.stream()
                            .filter(i -> consumedCodes.contains(i.productCode))
                            .collect(Collectors.toList());
                }

                long qualifyingPurchasePrice = 0;
                for (BasketItem item : newBasketItems) {
                    qualifyingPurchasePrice += toCents(item.price) * item.quantity;
                }

                if (qualifyingPurchasePrice < discountInCents) {
                    discountInCents = 0;
                }

                if (consumedBasket != null && !consumedBasket.isEmpty()) {
                    long consumedBasketPrice = 0;
                    for (BasketItem item : consumedBasket) {
                        consumedBasketPrice += toCents(item.price) * item.quantity;
                    }

                    if (consumedBasketPrice < discountInCents) {
                        discountInCents = 0;
                    }
                }
            }
        } else if (saveValueCode == 1) {
            long maxAmountToPurchase = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue : 0;

            List<BasketItem> newBasketItems = applicableBasketItems(basketItems, appliesToWhichItem);
            if (!newBasketItems.isEmpty()) {
                discountInCents = toCents(newBasketItems.get(0).price);
            }

            if (maxAmountToPurchase != 0 && discountInCents > maxAmountToPurchase) {
                discountInCents = maxAmountToPurchase;
            }

        } else if (saveValueCode == 2) {
            long freePurchaseItemUnits = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue : 0;

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
                        discountInCents += toCents(item.price);
                        index++;
                    }
                }
            }

        } else if (saveValueCode == 6) {
            discountInCents = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                    ? coupon.purchaseRequirement.primaryPurchaseSaveValue : 0;
        }

        return discountInCents;
    }

    public static List<BasketItem> applicableBasketItems(List<BasketItem> basketItems, Integer appliesToWhichItem) {
        List<BasketItem> result = new ArrayList<>();
        if (appliesToWhichItem == null) {
            result.addAll(basketItems);
        } else if (appliesToWhichItem == 0) {
            for (BasketItem item : basketItems) {
                if (item.purchaseType == null) result.add(item);
            }
        } else if (appliesToWhichItem == 1) {
            for (BasketItem item : basketItems) {
                if ("second_purchase".equals(item.purchaseType)) result.add(item);
            }
        } else if (appliesToWhichItem == 2) {
            for (BasketItem item : basketItems) {
                if ("third_purchase".equals(item.purchaseType)) result.add(item);
            }
        }
        return result;
    }

    public static int basketUnits(List<BasketItem> basket) {
        int total = 0;
        for (BasketItem item : basket) {
            total += item.quantity;
        }
        return total;
    }

    // =========================
    // Basket reduction
    // =========================
    public static ReduceBasketResult reduceBasket(
            List<BasketItem> basket,
            List<BasketItem> allowedBasketItems,
            UnitsToPurchaseHolder unitsHolder
    ) {
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
                            for (String otherKey : Arrays.asList("units_to_purchase", "units_to_purchase2", "units_to_purchase3")) {
                                Integer otherValue = unitsHolder.get(otherKey);
                                if (!otherKey.equals(key) && otherValue != null && otherValue > 0) {
                                    BasketItem allowedBasketItemOther = allowedBasketItemsIncludes(
                                            allowedBasketItems, consumedBasketItem, otherKey
                                    );
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

    public static BasketItem allowedBasketItemsIncludes(
            List<BasketItem> allowedBasketItems,
            BasketItem item,
            String keyUnitsToPurchase
    ) {
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

        Purchases purchasesObj = getPurchases(pr);
        Purchase primaryPurchase = purchasesObj.primaryPurchase;
        Purchase secondPurchase = purchasesObj.secondPurchase;
        Purchase thirdPurchase = purchasesObj.thirdPurchase;

        if (additionalPurchaseRulesCode == null) {
            MeetsPurchaseRequirementsResult result =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, true);
            return new MeetsRequirementsResult(result.status, result.basketItems, result.unitsToPurchase, null, null);

        } else if (additionalPurchaseRulesCode == 0) {
            List<Purchase> purchases = Arrays.asList(primaryPurchase, secondPurchase, thirdPurchase);
            List<String> purchaseTypes = Arrays.asList("", "second_purchase", "third_purchase");

            if (appliesToWhichItem == null) {
                for (BasketItem basketItem : basket) {
                    for (int i = 0; i < purchases.size(); i++) {
                        Purchase purchase = purchases.get(i);
                        if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {
                            MeetsPurchaseRequirementsResult result =
                                    meetsPurchaseRequirements(coupon, basket, purchase, true);

                            boolean basketItemFound = result.basketItems.stream()
                                    .anyMatch(item -> Objects.equals(item.productCode, basketItem.productCode));

                            if (result.status && basketItemFound) {
                                long totalValue = basketValue(result.basketItems);
                                long primarySaveValue = primaryPurchase.saveValue != null ? primaryPurchase.saveValue : 0;

                                if (totalValue >= primarySaveValue) {
                                    List<BasketItem> basketItems = copyBasketList(result.basketItems);
                                    if (i > 0) {
                                        for (BasketItem item : basketItems) {
                                            item.purchaseType = purchaseTypes.get(i);
                                        }
                                    }

                                    MeetsRequirementsResult out = new MeetsRequirementsResult();
                                    out.status = true;
                                    out.basketItems = basketItems;
                                    if (i == 0) out.unitsToPurchase = result.unitsToPurchase;
                                    if (i == 1) out.unitsToPurchase2 = result.unitsToPurchase;
                                    if (i == 2) out.unitsToPurchase3 = result.unitsToPurchase;
                                    return out;
                                }
                            }
                        }
                    }
                }
            } else if (appliesToWhichItem >= 0 && appliesToWhichItem <= 2) {
                Purchase purchase = purchases.get(appliesToWhichItem);
                if (purchase != null && purchase.reqCode != null && purchase.requirements != null) {
                    MeetsPurchaseRequirementsResult result =
                            meetsPurchaseRequirements(coupon, basket, purchase, true);
                    if (result.status) {
                        List<BasketItem> basketItems = copyBasketList(result.basketItems);
                        if (appliesToWhichItem > 0) {
                            for (BasketItem item : basketItems) {
                                item.purchaseType = purchaseTypes.get(appliesToWhichItem);
                            }
                        }

                        MeetsRequirementsResult out = new MeetsRequirementsResult();
                        out.status = true;
                        out.basketItems = basketItems;
                        if (appliesToWhichItem == 0) out.unitsToPurchase = result.unitsToPurchase;
                        if (appliesToWhichItem == 1) out.unitsToPurchase2 = result.unitsToPurchase;
                        if (appliesToWhichItem == 2) out.unitsToPurchase3 = result.unitsToPurchase;
                        return out;
                    }
                }
            }

        } else if (additionalPurchaseRulesCode == 1) {
            MeetsPurchaseRequirementsResult r1 =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);
            if (!r1.status) return MeetsRequirementsResult.negative();

            List<BasketItem> basketItems1 = r1.basketItems;
            Integer unitsToPurchase1 = r1.unitsToPurchase;

            List<BasketItem> basketItems2 = null;
            Integer unitsToPurchase2 = null;
            if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r2 =
                        meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
                if (!r2.status) return MeetsRequirementsResult.negative();
                basketItems2 = r2.basketItems;
                unitsToPurchase2 = r2.unitsToPurchase;
            }

            List<BasketItem> basketItems3 = null;
            Integer unitsToPurchase3 = null;
            if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r3 =
                        meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);
                if (!r3.status) return MeetsRequirementsResult.negative();
                basketItems3 = r3.basketItems;
                unitsToPurchase3 = r3.unitsToPurchase;
            }

            if (basketItems2 != null) {
                for (BasketItem item : basketItems2) item.purchaseType = "second_purchase";
            }
            if (basketItems3 != null) {
                for (BasketItem item : basketItems3) item.purchaseType = "third_purchase";
            }

            List<BasketItem> combined = new ArrayList<>();
            combined.addAll(basketItems1);
            if (basketItems2 != null) combined.addAll(basketItems2);
            if (basketItems3 != null) combined.addAll(basketItems3);

            List<BasketItem> basketItemsFinal = reorderSubBasket(basket, combined);

            return new MeetsRequirementsResult(true, basketItemsFinal, unitsToPurchase1, unitsToPurchase2, unitsToPurchase3);

        } else if (additionalPurchaseRulesCode == 2) {
            MeetsPurchaseRequirementsResult r1 =
                    meetsPurchaseRequirements(coupon, basket, primaryPurchase, false);
            if (!r1.status) return MeetsRequirementsResult.negative();

            List<BasketItem> basketItems1 = r1.basketItems;
            Integer unitsToPurchase1 = r1.unitsToPurchase;

            boolean status2 = false;
            List<BasketItem> basketItems2 = null;
            Integer unitsToPurchase2 = null;
            if (secondPurchase.reqCode != null && secondPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r2 =
                        meetsPurchaseRequirements(coupon, basket, secondPurchase, false);
                status2 = r2.status;
                basketItems2 = r2.basketItems;
                unitsToPurchase2 = r2.unitsToPurchase;
            }

            boolean status3 = false;
            List<BasketItem> basketItems3 = null;
            Integer unitsToPurchase3 = null;
            if (thirdPurchase.reqCode != null && thirdPurchase.requirements != null) {
                MeetsPurchaseRequirementsResult r3 =
                        meetsPurchaseRequirements(coupon, basket, thirdPurchase, false);
                status3 = r3.status;
                basketItems3 = r3.basketItems;
                unitsToPurchase3 = r3.unitsToPurchase;
            }

            if (status2 && basketItems2 != null) {
                for (BasketItem item : basketItems2) item.purchaseType = "second_purchase";
            } else if (status3 && basketItems3 != null) {
                for (BasketItem item : basketItems3) item.purchaseType = "third_purchase";
            } else {
                return MeetsRequirementsResult.negative();
            }

            List<BasketItem> combined = new ArrayList<>();
            combined.addAll(basketItems1);
            if (status2 && basketItems2 != null) combined.addAll(basketItems2);
            else if (status3 && basketItems3 != null) combined.addAll(basketItems3);

            List<BasketItem> basketItemsFinal = reorderSubBasket(basket, combined);

            return new MeetsRequirementsResult(true, basketItemsFinal, unitsToPurchase1, unitsToPurchase2, unitsToPurchase3);
        }

        return MeetsRequirementsResult.negative();
    }

    public static int getAdditionalUnitsToPurchase(
            Coupon coupon,
            List<BasketItem> basketItems,
            int unitsToPurchase,
            Purchase purchase
    ) {
        if (purchase.reqCode != null && purchase.reqCode == 0) {
            long totalPriceUnitsToPurchase = 0;
            int count = 0;
            int additionalUnitsToPurchase = 0;

            for (BasketItem item : basketItems) {
                for (int i = 0; i < item.quantity; i++) {
                    if (count < unitsToPurchase) {
                        totalPriceUnitsToPurchase += toCents(item.price);
                    } else {
                        long requiredSaveValue = coupon.purchaseRequirement.primaryPurchaseSaveValue != null
                                ? coupon.purchaseRequirement.primaryPurchaseSaveValue : 0;

                        if (requiredSaveValue > totalPriceUnitsToPurchase) {
                            additionalUnitsToPurchase++;
                            totalPriceUnitsToPurchase += toCents(item.price);
                        }
                    }
                    count++;
                }
            }

            return additionalUnitsToPurchase;
        }

        return 0;
    }

    public static MeetsPurchaseRequirementsResult meetsPurchaseRequirements(
            Coupon coupon,
            List<BasketItem> basket,
            Purchase purchase,
            boolean applyAdditionalUnits
    ) {
        int unitsToPurchase = 0;

        if (purchase.reqCode != null && purchase.reqCode == 0) {
            unitsToPurchase = purchase.requirements != null ? purchase.requirements.intValue() : 0;

         // ✅ STEP 1: filter only eligible items
            List<BasketItem> eligibleBasket = allowedBasket(basket, purchase);

            // ❗ IMPORTANT: if no eligible items → FAIL immediately
            if (eligibleBasket == null || eligibleBasket.isEmpty()) {
                return new MeetsPurchaseRequirementsResult(false, new ArrayList<>(), 0);
            }

            // ✅ STEP 2: pass ONLY filtered basket
            BasketHasUnitsResult result = basketHasUnitsToPurchase(
                    eligibleBasket,
                    unitsToPurchase,
                    purchase
            );
            
            if (applyAdditionalUnits) {
                unitsToPurchase += getAdditionalUnitsToPurchase(coupon, result.basketItems, unitsToPurchase, purchase);
            }

            return new MeetsPurchaseRequirementsResult(result.status, result.basketItems, unitsToPurchase);

        } else if (purchase.reqCode != null && purchase.reqCode == 1) {
            long cashValueTotalTransaction = 0;
            int unitCount = 0;

            List<BasketItem> newBasket = allowedBasket(basket, purchase);

            for (BasketItem item : newBasket) {
                for (int i = 0; i < item.quantity; i++) {
                    if (cashValueTotalTransaction < purchase.requirements) {
                        cashValueTotalTransaction += toCents(item.price);
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
                BasketItem copied = copyBasketItem(item);
                for (int i = 0; i < item.quantity; i++) {
                    if (cashValueTotalTransaction < purchase.requirements) {
                        cashValueTotalTransaction += toCents(item.price);
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

    public static BasketHasUnitsResult basketHasUnitsToPurchase(
            List<BasketItem> basket,
            int unitsToPurchase,
            Purchase purchase
    ) {
        List<BasketItem> allowedBasketItems = allowedBasket(basket, purchase);
        int unitsPurchased = 0;
        for (BasketItem item : allowedBasketItems) {
            unitsPurchased += item.quantity;
        }

        BasketHasUnitsResult result = new BasketHasUnitsResult();
        result.status = unitsPurchased >= unitsToPurchase;
        result.basketItems = allowedBasketItems;
        return result;
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

            if (excludedRange != null
                    && compareStringNumbers(item.productCode, excludedRange.start) >= 0
                    && compareStringNumbers(item.productCode, excludedRange.end) <= 0) {
                continue;
            }

            if ((purchase.excludedGtins != null && purchase.excludedGtins.contains(item.productCode))
                    || (purchase.excludedEans != null && purchase.excludedEans.contains(item.productCode))) {
                continue;
            }

            if (range != null
                    && (compareStringNumbers(item.productCode, range.start) < 0
                    || compareStringNumbers(item.productCode, range.end) > 0)) {
                continue;
            }

            allowedBasketItems.add(item);
        }

        return allowedBasketItems;
    }

    // =========================
    // Prefixed code transform
    // =========================
    public static Map<String, String[]> transformString(String input) {
        String[] parts = input.split(":");
        String key = parts[0];
        String[] values = parts[2].split("[-_]");
        Map<String, String[]> result = new HashMap<>();
        result.put(key, values);
        return result;
    }

    public static Map<String, Range> transformPrefixedCode(Object prefixedCode) {
        if (prefixedCode == null) {
            return new HashMap<>();
        }

        Map<String, Range> output = new HashMap<>();

        if (prefixedCode instanceof List<?>) {
            List<?> list = (List<?>) prefixedCode;

            for (Object obj : list) {
                if (obj instanceof String) {
                    String code = (String) obj;
                    if (code.contains(":")) {
                        Map<String, String[]> transformed = transformString(code);
                        for (Map.Entry<String, String[]> e : transformed.entrySet()) {
                            String[] range = e.getValue();
                            if (range.length >= 2) {
                                output.put(e.getKey(), new Range(range[0], range[1]));
                            }
                        }
                    }
                }
            }
            return output;
        }

        if (prefixedCode instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) prefixedCode;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String property = String.valueOf(entry.getKey());
                Object codeObj = entry.getValue();

                if (property.contains(":")) {
                    Map<String, String[]> transformed = transformString(property);
                    for (Map.Entry<String, String[]> e : transformed.entrySet()) {
                        String[] range = e.getValue();
                        if (range.length >= 2) {
                            output.put(e.getKey(), new Range(range[0], range[1]));
                        }
                    }
                    continue;
                }

                if (codeObj instanceof List<?>) {
                    List<?> codeList = (List<?>) codeObj;
                    if (!codeList.isEmpty()) {
                        String[] range = String.valueOf(codeList.get(0)).split("_");
                        if (range.length >= 2) {
                            output.put(property, new Range(range[0], range[1]));
                        }
                    }
                }
            }
        }

        return output;
    }

    // =========================
    // Purchases object builder
    // =========================
    public static Purchases getPurchases(PurchaseRequirement purchaseRequirement) {
        Purchases purchases = new Purchases();

        purchases.primaryPurchase = new Purchase();
        purchases.primaryPurchase.saveValue = purchaseRequirement.primaryPurchaseSaveValue;
        purchases.primaryPurchase.requirements = purchaseRequirement.primaryPurchaseRequirements;
        purchases.primaryPurchase.reqCode = purchaseRequirement.primaryPurchaseReqCode;
        purchases.primaryPurchase.gtins = purchaseRequirement.primaryPurchaseGtins;
        purchases.primaryPurchase.eans = purchaseRequirement.primaryPurchaseEans;
        purchases.primaryPurchase.excludedGtins = purchaseRequirement.excludedPrimaryPurchaseGtins;
        purchases.primaryPurchase.excludedEans = purchaseRequirement.excludedPrimaryPurchaseEans;
        purchases.primaryPurchase.prefixedCode = transformPrefixedCode(purchaseRequirement.primaryPurchasePrefixedCode);
        purchases.primaryPurchase.excludedPrefixedCode = transformPrefixedCode(purchaseRequirement.excludedPrimaryPurchasePrefixedCode);

        purchases.secondPurchase = new Purchase();
        purchases.secondPurchase.saveValue = purchaseRequirement.secondPurchaseSaveValue;
        purchases.secondPurchase.requirements = purchaseRequirement.secondPurchaseRequirements;
        purchases.secondPurchase.reqCode = purchaseRequirement.secondPurchaseReqCode;
        purchases.secondPurchase.gtins = purchaseRequirement.secondPurchaseGtins;
        purchases.secondPurchase.eans = purchaseRequirement.secondPurchaseEans;
        purchases.secondPurchase.excludedGtins = purchaseRequirement.excludedSecondPurchaseGtins;
        purchases.secondPurchase.excludedEans = purchaseRequirement.excludedSecondPurchaseEans;
        purchases.secondPurchase.prefixedCode = transformPrefixedCode(purchaseRequirement.secondPurchasePrefixedCode);
        purchases.secondPurchase.excludedPrefixedCode = transformPrefixedCode(purchaseRequirement.excludedSecondPurchasePrefixedCode);

        purchases.thirdPurchase = new Purchase();
        purchases.thirdPurchase.saveValue = purchaseRequirement.thirdPurchaseSaveValue;
        purchases.thirdPurchase.requirements = purchaseRequirement.thirdPurchaseRequirements;
        purchases.thirdPurchase.reqCode = purchaseRequirement.thirdPurchaseReqCode;
        purchases.thirdPurchase.gtins = purchaseRequirement.thirdPurchaseGtins;
        purchases.thirdPurchase.eans = purchaseRequirement.thirdPurchaseEans;
        purchases.thirdPurchase.excludedGtins = purchaseRequirement.excludedThirdPurchaseGtins;
        purchases.thirdPurchase.excludedEans = purchaseRequirement.excludedThirdPurchaseEans;
        purchases.thirdPurchase.prefixedCode = transformPrefixedCode(purchaseRequirement.thirdPurchasePrefixedCode);
        purchases.thirdPurchase.excludedPrefixedCode = transformPrefixedCode(purchaseRequirement.excludedThirdPurchasePrefixedCode);

        return purchases;
    }

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

    public static long basketValue(List<BasketItem> basket) {
        long totalValue = 0;
        for (BasketItem item : basket) {
            totalValue += toCents(item.price) * item.quantity;
        }
        return totalValue;
    }

    // =========================
    // Utility
    // =========================
    private static long toCents(double price) {
        return Math.round(price * 100);
    }

    private static double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static BasketItem copyBasketItem(BasketItem item) {
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

    private static List<BasketItem> copyBasketList(List<BasketItem> items) {
        List<BasketItem> out = new ArrayList<>();
        for (BasketItem item : items) {
            out.add(copyBasketItem(item));
        }
        return out;
    }

    private static int compareStringNumbers(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (Exception e) {
            return a.compareTo(b);
        }
    }

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

        public Range() {}

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

        public MeetsRequirementsResult() {}

        public MeetsRequirementsResult(
                boolean status,
                List<BasketItem> basketItems,
                Integer unitsToPurchase,
                Integer unitsToPurchase2,
                Integer unitsToPurchase3
        ) {
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