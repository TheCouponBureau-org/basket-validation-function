package com.tcb.validate.basket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
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

            // 🔥 Deserialize JSON → Java object
            ValidateBasket.BasketValidationInput input =
                    mapper.readValue(file, ValidateBasket.BasketValidationInput.class);

            // 🔥 Run your logic
            ValidateBasket.ValidationResult result =
                    ValidateBasket.validateBasketHelper(input);

            // 🔥 Convert to pretty JSON
            String output = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);

            System.out.println("\n===== SINGLE TEST OUTPUT =====");
            System.out.println(output);

            // ✅ Basic assertion (just check result exists)
            assertNotNull(result, "Result should not be null");

        } catch (Exception e) {
            fail("Test failed due to exception: " + e.getMessage());
        }
    }
}