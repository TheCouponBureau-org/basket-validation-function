package org.thecouponbureau.validate.basket;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class BasketValidationServiceTest {

	
	@Test
	@Tag("localBasketValidation")
	public void localBasketValidation() throws Exception {

	    BasketValidationService runner =
	            new BasketValidationService();

	    runner.localBasketValidation(
	            "POS_Basket_Validation_UseCases.xlsx", "localBasketValidation");

	}
	
    @Test
    @Tag("validateBasket")
    public void validateBasket() throws Exception {

        BasketValidationService runner =
                new BasketValidationService();

        runner.validateBasket(
                "POS_Basket_Validation_UseCases.xlsx", "validateBasket");
    }
    
}