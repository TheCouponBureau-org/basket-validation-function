package org.thecouponbureau.validate.basket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

public class BasketValidatorInputTest {

    @Test
    void validateBasketHelperAcceptsGs1StringCoupons() throws Exception {
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
        assertEquals("8112009988459000019133924009755364", input.coupons.get(0).gs1);
        assertNull(input.coupons.get(0).purchaseRequirement);
        assertNull(input.coupons.get(0).validated);
        assertNotNull(result.error);
        assertEquals("INVALID_INPUT", result.error.code);
    }

    @Test
    void validateBasketHelperAcceptsResolvedCouponObjects() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        String jsonInput = "{"
                + "\"basket\":[{\"product_code\":\"037000930396\",\"price\":1.29,\"quantity\":1,\"unit\":\"item\"}],"
                + "\"coupons\":[{"
                + "\"gs1\":\"8112009988459000019133924009755364\","
                + "\"validated\":true,"
                + "\"purchase_requirement\":{"
                + "\"primary_purchase_gtins\":[\"037000930396\"],"
                + "\"primary_purchase_save_value\":100,"
                + "\"primary_purchase_requirements\":1,"
                + "\"primary_purchase_req_code\":0,"
                + "\"save_value_code\":0"
                + "}"
                + "}]"
                + "}";

        BasketValidationInput input = mapper.readValue(jsonInput, BasketValidationInput.class);
        ValidationResult result = BasketValidator.validateBasketHelper(input);

        assertNotNull(input.coupons);
        assertEquals(1, input.coupons.size());
        assertEquals("8112009988459000019133924009755364", input.coupons.get(0).gs1);
        assertEquals(Boolean.TRUE, input.coupons.get(0).validated);
        assertNotNull(input.coupons.get(0).purchaseRequirement);
        assertNotNull(result.error);
        assertEquals("INVALID_INPUT", result.error.code);
    }
}
