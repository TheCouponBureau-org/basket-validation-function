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

	@Test
	@DisplayName("Coupon input rejects fields other than gs1")
	void rejectsCouponInputWithExtraFields() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			String jsonInput = "{"
					+ "\"basket\":[{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"}],"
					+ "\"coupons\":[{\"gs1\":\"8112209988459000320001\",\"base_gs1\":\"811220998845900032\"}]"
					+ "}";

			BasketValidationInput input = mapper.readValue(jsonInput, BasketValidationInput.class);
			ValidationResult result = BasketValidator.validateBasketHelper(input);

			assertNotNull(result);
			assertNotNull(result.error);
			assertEquals("INVALID_COUPON_INPUT", result.error.code);
			assertEquals("coupon input only supports gs1.", result.error.message);
			assertNotNull(result.error.details);
			assertEquals(0, result.error.details.get("coupon_index"));
			assertNotNull(result.basketValidationOutput);
			assertEquals(0, result.basketValidationOutput.discountInCents);
			assertFalse(result.notAllCouponsConsumed);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}
}
