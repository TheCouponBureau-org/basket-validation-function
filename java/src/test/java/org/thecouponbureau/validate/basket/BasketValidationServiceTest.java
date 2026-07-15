package org.thecouponbureau.validate.basket;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class BasketValidationServiceTest {

	@Test
	@Tag("single-json")
    public void validateSingleJson() throws Exception {

        BasketValidationService runner =
                new BasketValidationService();

        runner.validateJsonFile("input-gs1-only.json");

    }
	
	@Test
	@Tag("approach1")
	public void validateApproach1() throws Exception {

	    BasketValidationService runner =
	            new BasketValidationService();

	    runner.validateApproach1(
	            "POS_Basket_Validation_UseCases.xlsx");

	}
	
    @Test
    @Tag("approach2")
    public void validateApproach2() throws Exception {

        BasketValidationService runner =
                new BasketValidationService();

        runner.validateApproach2(
                "POS_Basket_Validation_UseCases.xlsx");
    }
    @Test
    @Tag("rollback")
    public void rollbackCoupons() throws Exception {

        BasketValidationService runner =
                new BasketValidationService();

        runner.rollbackCouponsFromFile("applied-gs1s.txt");

    }
}