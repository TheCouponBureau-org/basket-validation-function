package org.thecouponbureau.validate.basket.Services;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TcbCouponResolutionServiceTest {

    @Test
    void matchesRequestedGs1UsingMasterOfferFileWhenLastFourDigitsDiffer() {
        TcbCouponResolutionService.RedeemedCoupon redeemedCouponOne =
                new TcbCouponResolutionService.RedeemedCoupon();
        redeemedCouponOne.gs1 = "8112209988459000329165266614604064";
        redeemedCouponOne.masterOfferFile = "811220998845900032";

        TcbCouponResolutionService.RedeemedCoupon redeemedCouponTwo =
                new TcbCouponResolutionService.RedeemedCoupon();
        redeemedCouponTwo.gs1 = "8112209988459000349165768322093822";
        redeemedCouponTwo.masterOfferFile = "811220998845900034";

        TcbCouponResolutionService.RedeemedCoupon matchedCoupon =
                TcbCouponResolutionService.findMatchingRedeemedCoupon(
                        "8112209988459000320001",
                        List.of(redeemedCouponOne, redeemedCouponTwo));

        assertNotNull(matchedCoupon);
        assertEquals("8112209988459000329165266614604064", matchedCoupon.gs1);
        assertEquals("811220998845900032", matchedCoupon.masterOfferFile);
    }

    @Test
    void returnsNullWhenNoMatchingRedeemedCouponExists() {
        TcbCouponResolutionService.RedeemedCoupon redeemedCoupon =
                new TcbCouponResolutionService.RedeemedCoupon();
        redeemedCoupon.gs1 = "8112209988459000349165768322093822";
        redeemedCoupon.masterOfferFile = "811220998845900034";

        TcbCouponResolutionService.RedeemedCoupon matchedCoupon =
                TcbCouponResolutionService.findMatchingRedeemedCoupon(
                        "8112209988459000990001",
                        List.of(redeemedCoupon));

        assertNull(matchedCoupon);
    }
}
