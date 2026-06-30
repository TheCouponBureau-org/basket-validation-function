package org.thecouponbureau.validate.basket.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

public class BasketValidationCli {

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 4) {
            System.err.println(
                    "Usage: BasketValidationCli <input-json-string> [<tcb-base-url> <tcb-access-key> <tcb-secret-key>]");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        BasketValidationInput input =
                mapper.readValue(args[0], BasketValidationInput.class);

        if (args.length == 4) {
            input.tcbBaseUrl = args[1];
            input.tcbAccessKey = args[2];
            input.tcbSecretKey = args[3];
        }

        ValidationResult result = BasketValidator.validateBasketHelper(input);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
