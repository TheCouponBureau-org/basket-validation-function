package org.thecouponbureau.validate.basket;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.thecouponbureau.validate.basket.runners.BasketValidationServiceRunner;

public class BasketValidationServiceTest {

	@Test
	@Tag("single-json")
    public void validateSingleJson() throws Exception {

        BasketValidationServiceRunner runner =
                new BasketValidationServiceRunner();

        runner.validateJsonFile("input-gs1-only.json");

    }
	
	@Test
	@Tag("approach1")
	public void validateApproach1() throws Exception {

	    BasketValidationServiceRunner runner =
	            new BasketValidationServiceRunner();

	    runner.validateApproach1(
	            "POS_Basket_Validation_UseCases.xlsx");

	}
	
    @Test
    @Tag("approach2")
    public void validateApproach2() throws Exception {

        BasketValidationServiceRunner runner =
                new BasketValidationServiceRunner();

        runner.validateApproach2(
                "POS_Basket_Validation_UseCases.xlsx");
    }
    @Test
    @Tag("rollback")
    public void rollbackCoupons() throws Exception {

        BasketValidationServiceRunner runner =
                new BasketValidationServiceRunner();

        runner.rollbackCouponsFromFile("applied-gs1s.txt");

    }
}