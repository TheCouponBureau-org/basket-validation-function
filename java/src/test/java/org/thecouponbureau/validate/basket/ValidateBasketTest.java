package org.thecouponbureau.validate.basket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.thecouponbureau.validate.basket.helper.BasketHelper;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketItem;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateBasketTest {

	@Test
	@DisplayName("Local basket validation works with purchase requirements")
	void validateLocalBasketInputJson() {

		try {
			// ✅ Read from JSON file
			File file = new File("input.json");

			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			// ✅ FIXED
			LocalBasketValidationInput input =
					mapper.readValue(file, LocalBasketValidationInput.class);

			ValidationResult result = BasketValidator.localBasketValidation(input);

			// 🔥 Pretty print output
			String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

			System.out.println("\n===== SINGLE TEST OUTPUT =====");
			System.out.println(output);

			// ✅ Assertion
			assertNotNull(result, "Result should not be null");
			assertTrue(output.contains("\"discount_in_cents\""));
			assertTrue(output.contains("\"applied_coupons\""));
			assertFalse(output.contains("\"basket_validation_output\""));
			assertFalse(output.contains("\"error\""));

		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("Local basket validation ignores base_gs1 from older payloads")
	void localValidationIgnoresBaseGs1InCouponInput() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			String jsonInput = "{"
					+ "\"basket\":[{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"}],"
					+ "\"coupons\":[{"
					+ "\"gs1\":\"8112209988459000320001\","
					+ "\"base_gs1\":\"811220998845900032\","
					+ "\"purchase_requirement\":{"
					+ "\"primary_purchase_gtins\":[\"037000930396\"],"
					+ "\"primary_purchase_save_value\":100,"
					+ "\"primary_purchase_requirements\":1,"
					+ "\"primary_purchase_req_code\":0,"
					+ "\"save_value_code\":0"
					+ "}"
					+ "}]"
					+ "}";

			LocalBasketValidationInput input =
					mapper.readValue(jsonInput, LocalBasketValidationInput.class);
			ValidationResult result = BasketValidator.localBasketValidation(input);

			assertNotNull(result);
			assertNull(result.error);
			assertNotNull(result.basketValidationOutput);
			assertEquals(100, result.basketValidationOutput.discountInCents);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("Local basket validation rejects unsupported extra fields")
	void localValidationRejectsCouponInputWithUnsupportedExtraFields() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			String jsonInput = "{"
					+ "\"basket\":[{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"}],"
					+ "\"coupons\":[{"
					+ "\"gs1\":\"8112209988459000320001\","
					+ "\"product_type\":\"x\","
					+ "\"purchase_requirement\":{"
					+ "\"primary_purchase_gtins\":[\"037000930396\"],"
					+ "\"primary_purchase_save_value\":100,"
					+ "\"primary_purchase_requirements\":1,"
					+ "\"primary_purchase_req_code\":0,"
					+ "\"save_value_code\":0"
					+ "}"
					+ "}]"
					+ "}";

			LocalBasketValidationInput input =
					mapper.readValue(jsonInput, LocalBasketValidationInput.class);
			ValidationResult result = BasketValidator.localBasketValidation(input);

			assertNotNull(result);
			assertNotNull(result.error);
			assertEquals("INVALID_COUPON_INPUT", result.error.code);
			assertEquals("coupon input only supports gs1 and purchase_requirement.", result.error.message);
			assertNotNull(result.error.details);
			assertEquals(0, result.error.details.get("coupon_index"));
			assertNotNull(result.basketValidationOutput);
			assertEquals(0, result.basketValidationOutput.discountInCents);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("Local basket validation applies coupons with purchase requirements")
	void localValidationAppliesCouponsWithPurchaseRequirements() {
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

			LocalBasketValidationInput input =
					mapper.readValue(jsonInput, LocalBasketValidationInput.class);
			ValidationResult result = BasketValidator.localBasketValidation(input);

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

	@Test
	@DisplayName("TCB-backed validation accepts gs1 string arrays and requires credentials")
	void validateBasketHelperAcceptsGs1StringsAndRequiresCredentials() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

			String jsonInput = "{"
					+ "\"basket\":[{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"}],"
					+ "\"coupons\":[\"8112009988459000019133924009755364\"]"
					+ "}";

			BasketValidationInput input = mapper.readValue(jsonInput, BasketValidationInput.class);
			ValidationResult result = BasketValidator.validateBasketHelper(input);

			assertNotNull(input.coupons);
			assertEquals(1, input.coupons.size());
			assertEquals("8112009988459000019133924009755364", input.coupons.get(0));
			assertNotNull(result.error);
			assertEquals("INVALID_INPUT", result.error.code);
			assertEquals(
					"tcb_base_url, tcb_access_key, and tcb_access_token are required.",
					result.error.message);
		} catch (Exception e) {
			fail("Test failed due to exception: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("Applied coupon product codes are returned as one combined gtins list")
	void returnsAppliedCouponProductCodesAsCombinedGtins() {
		BasketItem primary = new BasketItem();
		primary.productCode = "037000930396";

		BasketItem secondary = new BasketItem();
		secondary.productCode = "7106919588011";
		secondary.purchaseGroup = "second_purchase";

		BasketItem third = new BasketItem();
		third.productCode = "037000925033";
		third.purchaseGroup = "third_purchase";

		Map<String, List<String>> productCodes = BasketHelper.getProductCodes(
				List.of(primary, secondary, third));

		assertEquals(
				List.of("037000930396", "7106919588011", "037000925033"),
				productCodes.get("gtins"));
	}
}
