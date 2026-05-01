package org.thecouponbureau.validate.basket;

import org.junit.jupiter.api.Test;
import org.thecouponbureau.validate.basket.Services.ValidationSummary;
import org.thecouponbureau.validate.basket.runners.ExcelValidationRunner;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;

public class ExcelValidationTest {

	@Test()
	@DisplayName("Validate all Excel single-use test cases")
	void validateAllExcelCases() {

		ExcelValidationRunner runner = new ExcelValidationRunner();
		ValidationSummary summary = runner.runValidation();

		System.out.println("\n📊 FINAL RESULT:");
		System.out.println("Processed: " + summary.getProcessed());
		System.out.println("Passed   : " + summary.getPassed());
		System.out.println("Failed   : " + summary.getFailed());
		System.out.println("Skipped  : " + summary.getSkipped());
		System.out.println("Failed Rows: " + summary.getFailedRows());

		assertEquals(0, summary.getFailed(), "Some test cases failed. Rows: " + summary.getFailedRows());
	}
}