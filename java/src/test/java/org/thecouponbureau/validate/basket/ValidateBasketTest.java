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
			assertEquals("coupon input only supports gs1 and optional purchase_requirement.", result.error.message);
			assertNotNull(result.error.details);
			assertEquals(0, result.error.details.get("coupon_index"));
			assertNotNull(result.basketValidationOutput);
			assertEquals(0, result.basketValidationOutput.discountInCents);
			assertFalse(result.notAllCouponsConsumed);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("Coupon input allows purchase requirement and filters inapplicable coupons")
	void allowsPurchaseRequirementAndFiltersInapplicableCoupons() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			String jsonInput = "{"
					+ "\"basket\":["
					+ "{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"},"
					+ "{\"product_code\":\"037000934677\",\"price\":1.34,\"quantity\":1,\"unit\":\"item\"}"
					+ "],"
					+ "\"coupons\":["
					+ "{"
					+ "\"gs1\":\"8112009988459000019133924009755364\","
					+ "\"purchase_requirement\":{"
					+ "\"primary_purchase_gtins\":[\"037000930396\",\"037000934677\"],"
					+ "\"primary_purchase_save_value\":100,"
					+ "\"primary_purchase_requirements\":2,"
					+ "\"primary_purchase_req_code\":0,"
					+ "\"save_value_code\":0"
					+ "}"
					+ "},"
					+ "{"
					+ "\"gs1\":\"8112009988459000019133222024880382\","
					+ "\"purchase_requirement\":{"
					+ "\"primary_purchase_gtins\":[\"999999999999\"],"
					+ "\"primary_purchase_save_value\":100,"
					+ "\"primary_purchase_requirements\":1,"
					+ "\"primary_purchase_req_code\":0,"
					+ "\"save_value_code\":0"
					+ "}"
					+ "}"
					+ "]"
					+ "}";

			BasketValidationInput input = mapper.readValue(jsonInput, BasketValidationInput.class);
			ValidationResult result = BasketValidator.validateBasketHelper(input);

			assertNotNull(result);
			assertNull(result.error);
			assertNotNull(result.basketValidationOutput);
			assertEquals(100, result.basketValidationOutput.discountInCents);
			assertEquals(1, result.basketValidationOutput.appliedCoupons.size());
			assertEquals(
					"8112009988459000019133924009755364",
					result.basketValidationOutput.appliedCoupons.get(0).couponCode);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}
}
