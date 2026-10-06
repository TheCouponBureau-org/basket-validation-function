package org.thecouponbureau.validate.basket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class BasketValidationServiceTest {

	@Test
	@Tag("localBasketValidation")
	public void localBasketValidation() throws Exception {

		BasketValidationService runner = new BasketValidationService("https://api.try.thecouponbureau.org/",
				"8053fd0f80cf3778659def1359cac218", "eb42623aa2675e50f15da4f6d4aa0ad6", "", "");

		runner.localBasketValidation("POS_Basket_Validation_UseCases.xlsx", "localBasketValidation");

	}

	@Test
	@Tag("validateBasket")
	public void validateBasket() throws Exception {
		
		
		BasketValidationService runner = new BasketValidationService("https://api.try.thecouponbureau.org/",
				"8053fd0f80cf3778659def1359cac218", "eb42623aa2675e50f15da4f6d4aa0ad6", "", "");

		/*BasketValidationService runner = new BasketValidationService("https://api.try.thecouponbureau.org/",
				"d977468da5969dd03ffe4864043f21a7", "6d3e571583e0ffdb479ad0ef913cb42e", "accelerator",
				"automationretailer1.thecouponbureau.org");*/

		runner.validateBasket("LoyaltyLane_POS_Basket_Validation_UseCases.xlsx", "validateBasket");
	}

	@Test
	@Tag("validateBasketWithLocalRedis")
	public void validateBasketWithLocalRedis() throws Exception {

		BasketValidationService runner = new BasketValidationService("https://api.try.thecouponbureau.org/",
				"8053fd0f80cf3778659def1359cac218", "eb42623aa2675e50f15da4f6d4aa0ad6", "", "");

		runner.validateBasketWithLocalRedis("POS_Basket_Validation_UseCases.xlsx", "validateBasket");
	}

	@Test
	@Tag("single-json")
	public void validateSingleJson() throws Exception {

		BasketValidationService runner = new BasketValidationService("https://api.try.thecouponbureau.org/",
				"8053fd0f80cf3778659def1359cac218", "eb42623aa2675e50f15da4f6d4aa0ad6", "", "");

		runner.validateJsonFile("input-gs1-only.json");

	}

}