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
	public void localBasketValidation() throws Exception {

	    BasketValidationService runner =
	            new BasketValidationService();

	    runner.localBasketValidation(
	            "POS_Basket_Validation_UseCases.xlsx", "localBasketValidation");

	}
	
    @Test
    @Tag("approach2")
    public void validateBasket() throws Exception {

        BasketValidationService runner =
                new BasketValidationService();

        runner.validateBasket(
                "POS_Basket_Validation_UseCases.xlsx", "validateBasket");
    }
    
}