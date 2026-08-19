package org.thecouponbureau.validate.basket.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.model.basketValidationResults.Coupon;

public class BasketValidatorOrderTest {

    @Test
    void sortCouponsByOriginalOrderRestoresInputSequence() {
        Coupon secondInputCoupon = new Coupon();
        secondInputCoupon.gs1 = "8112209988459000359165000000000001";
        secondInputCoupon.internalOriginalIndex = 1;
        secondInputCoupon.internalResolvedSequence = 0;

        Coupon firstInputCoupon = new Coupon();
        firstInputCoupon.gs1 = "8112009988459000019133220584399722";
        firstInputCoupon.internalOriginalIndex = 0;
        firstInputCoupon.internalResolvedSequence = 0;

        Coupon thirdInputCoupon = new Coupon();
        thirdInputCoupon.gs1 = "8112009988459000039133742458606199";
        thirdInputCoupon.internalOriginalIndex = 2;
        thirdInputCoupon.internalResolvedSequence = 0;

        List<Coupon> coupons = new ArrayList<>(
                List.of(secondInputCoupon, thirdInputCoupon, firstInputCoupon));

        BasketValidator.sortCouponsByOriginalOrder(coupons);

        assertEquals("8112009988459000019133220584399722", coupons.get(0).gs1);
        assertEquals("8112209988459000359165000000000001", coupons.get(1).gs1);
        assertEquals("8112009988459000039133742458606199", coupons.get(2).gs1);
    }
}
