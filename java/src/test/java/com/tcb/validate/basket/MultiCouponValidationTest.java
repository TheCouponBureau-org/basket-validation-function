package com.tcb.validate.basket;

import com.tcb.validate.basket.Services.ValidationSummary;
import com.tcb.validate.basket.runners.MultiCouponExcelValidationRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

public class MultiCouponValidationTest {

    @Test
    @DisplayName("Validate Multi-Coupon Excel Test Cases")
    void validateMultiCouponCases() {

        MultiCouponExcelValidationRunner runner = new MultiCouponExcelValidationRunner();

        ValidationSummary summary = runner.runValidation();

        // 📊 Print Final Summary
        System.out.println("\n📊 MULTI-COUPON FINAL RESULT:");
        System.out.println("Processed: " + summary.getProcessed());
        System.out.println("Passed   : " + summary.getPassed());
        System.out.println("Failed   : " + summary.getFailed());
        System.out.println("Skipped  : " + summary.getSkipped());
        System.out.println("Failed Rows: " + summary.getFailedRows());

        // ❌ Fail test if any failure
        assertEquals(0, summary.getFailed(),
                "Multi-coupon failures at rows: " + summary.getFailedRows());
    }
}