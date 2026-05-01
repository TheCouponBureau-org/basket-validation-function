package org.thecouponbureau.validate.basket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;
import org.junit.jupiter.api.DisplayName;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateBasketTest {

	@Test
	@DisplayName("Single Input JSON Validation Test")
	void validateSingleInputJson() {

		try {
			// ✅ Read from JSON file
			File file = new File("input.json");

			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			// ✅ FIXED
			BasketValidationInput input = mapper.readValue(file, BasketValidationInput.class);

			// ✅ FIXED
			ValidationResult result = BasketValidator.validateBasketHelper(input);

			// 🔥 Pretty print output
			String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

			System.out.println("\n===== SINGLE TEST OUTPUT =====");
			System.out.println(output);

			// ✅ Assertion
			assertNotNull(result, "Result should not be null");

		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}
}