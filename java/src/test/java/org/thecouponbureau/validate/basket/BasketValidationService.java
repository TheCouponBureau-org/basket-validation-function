package org.thecouponbureau.validate.basket;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService;
import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService;
import org.thecouponbureau.validate.basket.Services.TcbTokenService;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.InputCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.LocalBasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.PurchaseRequirement;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import static org.junit.jupiter.api.Assertions.fail;

import org.thecouponbureau.validate.basket.Services.TcbMofSyncService;
import org.thecouponbureau.validate.basket.Services.TcbScannedGs1Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

public class BasketValidationService {

	private static final Logger logger = LogManager.getLogger(BasketValidationService.class);

	private void setTcbConfiguration(BasketValidationInput input) {

		input.tcbBaseUrl = "https://api.try.thecouponbureau.org/";
		input.tcbAccessKey = "8053fd0f80cf3778659def1359cac218";
		input.tcbAccessToken = TcbTokenService.fetchAccessToken(input.tcbBaseUrl, input.tcbAccessKey,
				"eb42623aa2675e50f15da4f6d4aa0ad6");

	}

	public void validateJsonFile(String jsonFile) throws Exception {

		String jsonPath = System.getProperty("user.dir") + "/" + jsonFile;

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String inputJson = Files.readString(Paths.get(jsonPath));

		BasketValidationInput input = mapper.readValue(inputJson, BasketValidationInput.class);

		setTcbConfiguration(input);

		ValidationResult result = BasketValidator.validateBasketHelper(input);

		logger.info(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));

		List<String> gs1List = new ArrayList<>();

		for (AppliedCoupon coupon : result.basketValidationOutput.appliedCoupons) {

			gs1List.add(coupon.couponCode);

		}

		if (!gs1List.isEmpty()) {

			String redeemResponse = null;

			try {

				logger.info("====================================");
				logger.info("Redeeming Coupons");
				logger.info("====================================");
				logger.info("Coupons: {}", gs1List);

				redeemResponse = TcbCouponRedeemService.redeemCoupons(input.tcbBaseUrl, input.tcbAccessKey,
						input.tcbAccessToken, gs1List);

				logger.info("====================================");
				logger.info("Redeem Response");
				logger.info("====================================");
				logger.info(redeemResponse);

			} catch (Exception e) {

				logger.info("====================================");
				logger.info("REDEEM FAILED");
				logger.info("====================================");

				logger.info(e.getMessage());
				logger.error("Redeem failed", e);

				throw e;

			} finally {

				if (redeemResponse == null) {

					logger.info("Redeem was not successful. Rollback skipped.");
					return;
				}

				try {

					List<String> rollbackGs1List = new ArrayList<>();

					JsonNode redeemNode = mapper.readTree(redeemResponse);

					JsonNode redeemedCoupons = redeemNode.get("newly_redeemed");

					if (redeemedCoupons != null) {

						for (JsonNode coupon : redeemedCoupons) {

							rollbackGs1List.add(coupon.get("gs1").asText());

						}
					}

					logger.info("====================================");
					logger.info("Rolling Back Coupons");
					logger.info("====================================");
					logger.info(rollbackGs1List);

					Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(input.tcbBaseUrl,
							input.tcbAccessKey, input.tcbAccessToken, rollbackGs1List);

					logger.info("Rollback Response:");

					for (Map.Entry<String, String> entry : rollbackResponses.entrySet()) {

						logger.info("----------------------------------------");
						logger.info("GS1 : " + entry.getKey());
						logger.info(entry.getValue());

					}

				} catch (Exception e) {

					logger.info("====================================");
					logger.info("ROLLBACK FAILED");
					logger.info("====================================");

					logger.info("Coupons attempted:");

					for (String gs1 : gs1List) {
						logger.info(gs1);
					}

					logger.error("Rollback failed", e);

					throw e;
				}

			}

		} else {

			logger.info("No coupons applied. Redeem/Rollback skipped.");

		}
	}

	public void localBasketValidation(String excelFile, String sheetName) throws Exception {

		logger.info("====================================");
		logger.info("Executing localBasketValidation");
		logger.info("====================================");

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String excelPath = System.getProperty("user.dir") + "/" + excelFile;

		Workbook workbook = new XSSFWorkbook(new FileInputStream(excelPath));

		Sheet sheet = workbook.getSheet(sheetName);

		int processed = 0;
		int passed = 0;
		int failed = 0;
		int skipped = 0;

		List<Integer> failedRows = new ArrayList<>();

		for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {

			Row row = sheet.getRow(rowNum);

			if (row == null) {
				skipped++;
				continue;
			}

			if (row.getCell(5) == null || row.getCell(5).toString().trim().isEmpty()) {
				skipped++;
				continue;
			}

			String scenario = "";

			if (row.getCell(0) != null) {
				scenario = row.getCell(0).toString().trim();
			}

			String inputJson = row.getCell(5).getStringCellValue().trim();

			String expectedJson = "";
			if (row.getCell(7) != null) {
				expectedJson = row.getCell(7).getStringCellValue().trim();
			}

			LocalBasketValidationInput input = mapper.readValue(inputJson, LocalBasketValidationInput.class);

			// setTcbConfiguration(input);

			ValidationResult actualResult = BasketValidator.localBasketValidation(input);

			String actualJson = mapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(actualResult.basketValidationOutput);

			JsonNode expectedNode = mapper.readTree(expectedJson);
			JsonNode actualNode = mapper.readTree(actualJson);

			processed++;

			if (expectedNode.equals(actualNode)) {

				passed++;

				logger.info("===========================================================");
				logger.info("➡️ Processing row: " + (rowNum + 1));
				logger.info("===========================================================");

				logger.info("Scenario :");
				logger.info(scenario);

				logger.info("✅ PASS");

			} else {

				failed++;
				failedRows.add(rowNum + 1);

				logger.info("===========================================================");
				logger.info("➡️ Processing row: " + (rowNum + 1));
				logger.info("===========================================================");

				logger.info("Scenario :");
				logger.info(scenario);

				logger.error("❌ FAIL");

				logger.info("Basket Validation Input:");
				logger.info(inputJson);

				logger.info("Expected Output:");
				logger.info(expectedJson);

				logger.info("Actual Output:");
				logger.info(actualJson);
			}
		}

		workbook.close();

		logger.info("====================================");
		logger.info("📊 FINAL RESULT");
		logger.info("====================================");

		logger.info("Processed : " + processed);
		logger.info("Passed    : " + passed);
		logger.info("Failed    : " + failed);
		logger.info("Skipped   : " + skipped);

		logger.info("Failed Rows:");
		logger.info(failedRows);
	}

	public void validateBasket(String excelFile, String sheetName) throws Exception {

		logger.info("====================================");
		logger.info("Executing validateBasket");
		logger.info("====================================");

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String excelPath = System.getProperty("user.dir") + "/" + excelFile;

		Workbook workbook = new XSSFWorkbook(new FileInputStream(excelPath));

		Sheet sheet = workbook.getSheet(sheetName);

		int processed = 0;
		int passed = 0;
		int failed = 0;
		int skipped = 0;

		List<Integer> failedRows = new ArrayList<>();

		for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {

			Row row = sheet.getRow(rowNum);

			if (row == null) {
				skipped++;
				continue;
			}

			if (row.getCell(5) == null || row.getCell(5).toString().trim().isEmpty()) {
				skipped++;
				continue;
			}

			String scenario = "";

			if (row.getCell(0) != null) {
				scenario = row.getCell(0).toString().trim();
			}

			String inputJson = row.getCell(5).getStringCellValue().trim();

			String expectedJson = "";

			if (row.getCell(7) != null) {
				expectedJson = row.getCell(7).getStringCellValue().trim();
			}

			processed++;

			try {

				BasketValidationInput input = mapper.readValue(inputJson, BasketValidationInput.class);

				setTcbConfiguration(input);

				ValidationResult result = BasketValidator.validateBasketHelper(input);
				String actualJson = mapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(result.basketValidationOutput);

				JsonNode expectedNode = mapper.readTree(expectedJson);
				JsonNode actualNode = mapper.readTree(actualJson);

				normalizeCouponCodes(expectedNode, actualNode);

				if (!expectedNode.equals(actualNode)) {

					failed++;
					failedRows.add(rowNum + 1);

					logger.info("===========================================================");
					logger.info("➡️ Processing row: " + (rowNum + 1));
					logger.info("===========================================================");

					logger.info("");
					logger.info("Scenario :");
					logger.info(scenario);

					logger.info("");
					logger.error("❌ VALIDATION FAILED");

					logger.info("");
					logger.info("Basket Validation Input:");
					logger.info(inputJson);

					logger.info("");
					logger.info("Expected Output:");
					logger.info(expectedJson);

					logger.info("");
					logger.info("Actual Output:");
					logger.info(actualJson);

					continue;
				}

				List<String> gs1List = new ArrayList<>();

				for (AppliedCoupon coupon : result.basketValidationOutput.appliedCoupons) {

					gs1List.add(coupon.couponCode);

				}

				if (!gs1List.isEmpty()) {

					List<String> rollbackGs1List = new ArrayList<>();

					try {

						logger.info("===========================================================");
						logger.info("➡️ Processing row: " + (rowNum + 1));
						logger.info("===========================================================");

						logger.info("");
						logger.info("Scenario :");
						logger.info(scenario);

						String redeemResponse = TcbCouponRedeemService.redeemCoupons(input.tcbBaseUrl,
								input.tcbAccessKey, input.tcbAccessToken, gs1List);

						logger.info("");
						logger.info("Redeem Response:");
						logger.info(redeemResponse);

						JsonNode redeemNode = mapper.readTree(redeemResponse);

						JsonNode newlyRedeemed = redeemNode.get("newly_redeemed");

						if (newlyRedeemed != null) {

							for (JsonNode coupon : newlyRedeemed) {

								rollbackGs1List.add(coupon.get("gs1").asText());

							}

						}

					} finally {

						if (!rollbackGs1List.isEmpty()) {

							logger.info("");
							logger.info("Rolling Back Coupons:");
							logger.info(rollbackGs1List);

							Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
									input.tcbBaseUrl, input.tcbAccessKey, input.tcbAccessToken, rollbackGs1List);

							logger.info("Rollback Response:");

							for (Map.Entry<String, String> entry : rollbackResponses.entrySet()) {

								logger.info("");
								logger.info("----------------------------------------");
								logger.info("GS1 : " + entry.getKey());
								logger.info(entry.getValue());

							}
						} else {

							logger.info("");
							logger.info("No redeemed coupons found for rollback.");

						}
					}

					passed++;

					logger.info("");
					logger.info("✅ PASS");

				} else {

					passed++;

					logger.info("===========================================================");
					logger.info("➡️ Processing row: " + (rowNum + 1));
					logger.info("===========================================================");

					logger.info("");
					logger.info("Scenario :");
					logger.info(scenario);

					logger.info("");
					logger.info("No coupons applied. Redeem/Rollback skipped.");

					logger.info("");
					logger.info("✅ PASS");
					logger.info("");
				}
			} catch (Exception e) {

				failed++;
				failedRows.add(rowNum + 1);

				logger.info("===========================================================");
				logger.info("➡️ Processing row: " + (rowNum + 1));
				logger.info("===========================================================");

				logger.info("");
				logger.info("Scenario :");
				logger.info(scenario);

				logger.info("");
				logger.error("❌ FAIL");

				logger.info("");
				logger.info("Basket Validation Input:");
				logger.info(inputJson);

				logger.error("Exception occurred", e);
			}
		}

		workbook.close();

		logger.info("====================================");
		logger.info("📊 FINAL RESULT");
		logger.info("====================================");

		logger.info("Processed : " + processed);
		logger.info("Passed    : " + passed);
		logger.info("Failed    : " + failed);
		logger.info("Skipped   : " + skipped);

		if (!failedRows.isEmpty()) {
			fail("Validation failed for rows: " + failedRows);
		}
		// logger.info("Failed Rows:");
		// logger.info(failedRows);
	}

	public void validateBasketWithLocalRedis(String excelFile, String sheetName) throws Exception {

		boolean sync_mof_data = false;
		logger.info("====================================");
		logger.info("Executing validateBasket With Loacl Redis");
		logger.info("====================================");

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String excelPath = System.getProperty("user.dir") + "/" + excelFile;

		Workbook workbook = new XSSFWorkbook(new FileInputStream(excelPath));

		Sheet sheet = workbook.getSheet(sheetName);

		int processed = 0;
		int passed = 0;
		int failed = 0;
		int skipped = 0;

		List<Integer> failedRows = new ArrayList<>();

		for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {

			Row row = sheet.getRow(rowNum);

			if (row == null) {
				skipped++;
				continue;
			}

			if (row.getCell(5) == null || row.getCell(5).toString().trim().isEmpty()) {
				skipped++;
				continue;
			}

			String scenario = "";

			if (row.getCell(0) != null) {
				scenario = row.getCell(0).toString().trim();
			}

			String inputJson = row.getCell(5).getStringCellValue().trim();

			String expectedJson = "";

			if (row.getCell(7) != null) {
				expectedJson = row.getCell(7).getStringCellValue().trim();
			}

			processed++;

			try {

				BasketValidationInput input = mapper.readValue(inputJson, BasketValidationInput.class);

				setTcbConfiguration(input);

				if (!sync_mof_data) {

					/**
					 * Sync MOF data with purchase requirements and store in local Redis
					 */
					ObjectMapper mapper1 = new ObjectMapper();
					String pageNo = "";

					while (true) {

						TcbMofSyncService.SyncMofResponse mofResponse = TcbMofSyncService.syncMasterOfferFiles(
								input.tcbBaseUrl, input.tcbAccessKey, input.tcbAccessToken, "initial", pageNo);

						logger.info("Total MOF Records fetched = " + mofResponse.data.size());

						try (Jedis jedis = new Jedis("localhost", 6379)) {

							for (TcbMofSyncService.MasterOfferFileRecord record : mofResponse.data) {

								String purchaseRequirementJson = mapper1.writeValueAsString(record.purchaseRequirement);

								logger.info("base_gs1 = " + record.baseGs1);
								logger.info("purchaseRequirementJson = " + purchaseRequirementJson);

								jedis.set(record.baseGs1, purchaseRequirementJson);
							}
						}

						logger.info("nextPageNo = " + mofResponse.nextPageNo);

						// Stop when there are no more pages
						if ("-1".equals(String.valueOf(mofResponse.nextPageNo))) {
							logger.info("MOF sync completed. Reached last page.");
							break;
						}

						pageNo = String.valueOf(mofResponse.nextPageNo);

					}

					sync_mof_data = true;
				}

				/**
				 * Get the coupons array from input
				 */
				Set<String> seen = new HashSet<>();
				List<String> inputCoupons = input.coupons.stream()
				        .map(coupon -> coupon.gs1)
				        .filter(gs1 -> gs1 != null && !gs1.isEmpty())
				        .filter(gs1 -> gs1.startsWith("81122") || seen.add(gs1))
				        .toList();

				/**
				 * Get the array of coupons with purchase requirements
				 */
				List<InputCoupon> inputCouponsWithPurchaseRequirements = buildCouponsFromLocalDb(input.tcbBaseUrl,
						input.tcbAccessKey, input.tcbAccessToken, inputCoupons);

				/**
				 * Replace actual input coupons with input + purchase requirements
				 */
				if (inputCouponsWithPurchaseRequirements != null) {
					input.coupons = inputCouponsWithPurchaseRequirements;
				}

				ValidationResult result = BasketValidator.validateBasketHelper(input);
				String actualJson = mapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(result.basketValidationOutput);

				JsonNode expectedNode = mapper.readTree(expectedJson);
				JsonNode actualNode = mapper.readTree(actualJson);

				normalizeCouponCodes(expectedNode, actualNode);

				if (!expectedNode.equals(actualNode)) {

					failed++;
					failedRows.add(rowNum + 1);

					logger.info("===========================================================");
					logger.info("➡️ Processing row: " + (rowNum + 1));
					logger.info("===========================================================");

					logger.info("");
					logger.info("Scenario :");
					logger.info(scenario);

					logger.info("");
					logger.error("❌ VALIDATION FAILED");

					logger.info("");
					logger.info("Basket Validation Input:");
					logger.info(inputJson);

					logger.info("");
					logger.info("Expected Output:");
					logger.info(expectedJson);

					logger.info("");
					logger.info("Actual Output:");
					logger.info(actualJson);

					continue;
				}

				List<String> gs1List = new ArrayList<>();

				for (AppliedCoupon coupon : result.basketValidationOutput.appliedCoupons) {

					gs1List.add(coupon.couponCode);

				}

				if (!gs1List.isEmpty()) {

					List<String> rollbackGs1List = new ArrayList<>();

					try {

						logger.info("===========================================================");
						logger.info("➡️ Processing row: " + (rowNum + 1));
						logger.info("===========================================================");

						logger.info("");
						logger.info("Scenario :");
						logger.info(scenario);

						String redeemResponse = TcbCouponRedeemService.redeemCoupons(input.tcbBaseUrl,
								input.tcbAccessKey, input.tcbAccessToken, gs1List);

						logger.info("");
						logger.info("Redeem Response:");
						logger.info(redeemResponse);

						JsonNode redeemNode = mapper.readTree(redeemResponse);

						JsonNode newlyRedeemed = redeemNode.get("newly_redeemed");

						if (newlyRedeemed != null) {

							for (JsonNode coupon : newlyRedeemed) {

								rollbackGs1List.add(coupon.get("gs1").asText());

							}

						}

					} finally {

						if (!rollbackGs1List.isEmpty()) {

							logger.info("");
							logger.info("Rolling Back Coupons:");
							logger.info(rollbackGs1List);

							Map<String, String> rollbackResponses = TcbCouponRollbackService.rollbackCoupons(
									input.tcbBaseUrl, input.tcbAccessKey, input.tcbAccessToken, rollbackGs1List);

							logger.info("Rollback Response:");

							for (Map.Entry<String, String> entry : rollbackResponses.entrySet()) {

								logger.info("");
								logger.info("----------------------------------------");
								logger.info("GS1 : " + entry.getKey());
								logger.info(entry.getValue());

							}
						} else {

							logger.info("");
							logger.info("No redeemed coupons found for rollback.");

						}
					}

					passed++;

					logger.info("");
					logger.info("✅ PASS");

				} else {

					passed++;

					logger.info("===========================================================");
					logger.info("➡️ Processing row: " + (rowNum + 1));
					logger.info("===========================================================");

					logger.info("");
					logger.info("Scenario :");
					logger.info(scenario);

					logger.info("");
					logger.info("No coupons applied. Redeem/Rollback skipped.");

					logger.info("");
					logger.info("✅ PASS");
					logger.info("");
				}
			} catch (Exception e) {

				failed++;
				failedRows.add(rowNum + 1);

				logger.info("===========================================================");
				logger.info("➡️ Processing row: " + (rowNum + 1));
				logger.info("===========================================================");

				logger.info("");
				logger.info("Scenario :");
				logger.info(scenario);

				logger.info("");
				logger.error("❌ FAIL");

				logger.info("");
				logger.info("Basket Validation Input:");
				logger.info(inputJson);

				logger.error("Exception occurred", e);
			}
		}

		workbook.close();

		logger.info("====================================");
		logger.info("📊 FINAL RESULT");
		logger.info("====================================");

		logger.info("Processed : " + processed);
		logger.info("Passed    : " + passed);
		logger.info("Failed    : " + failed);
		logger.info("Skipped   : " + skipped);

		if (!failedRows.isEmpty()) {
			fail("Validation failed for rows: " + failedRows);
		}
		// logger.info("Failed Rows:");
		// logger.info(failedRows);
	}

	private void normalizeCouponCodes(JsonNode expectedNode, JsonNode actualNode) {

		JsonNode expectedCoupons = expectedNode.get("applied_coupons");
		JsonNode actualCoupons = actualNode.get("applied_coupons");

		if (expectedCoupons == null || actualCoupons == null) {
			return;
		}

		for (int i = 0; i < Math.min(expectedCoupons.size(), actualCoupons.size()); i++) {

			JsonNode expectedCoupon = expectedCoupons.get(i);
			JsonNode actualCoupon = actualCoupons.get(i);

			JsonNode expectedCodeNode = expectedCoupon.get("coupon_code");
			JsonNode actualCodeNode = actualCoupon.get("coupon_code");

			if (expectedCodeNode == null || actualCodeNode == null) {
				continue;
			}

			String expectedCode = expectedCodeNode.asText();

			if (expectedCode.endsWith("<>")) {

				String prefix = expectedCode.substring(0, expectedCode.length() - 2);

				if (actualCodeNode.asText().startsWith(prefix)) {

					((com.fasterxml.jackson.databind.node.ObjectNode) actualCoupon).put("coupon_code", expectedCode);
				}
			}
		}
	}

	private static List<InputCoupon> buildCouponsFromLocalDb(String tcbBaseUrl, String tcbAccessKey, String accessToken,
			List<String> inputCoupons) throws Exception {

		List<String> scannedCoupons = inputCoupons;

		List<TcbScannedGs1Service.SerializedGs1Data> resolvedCoupons = TcbScannedGs1Service.parseScannedGs1s(tcbBaseUrl,
				tcbAccessKey, accessToken, scannedCoupons);

		ObjectMapper mapper = new ObjectMapper();
		List<InputCoupon> coupons = new ArrayList<>();

		try (Jedis jedis = new Jedis("localhost", 6379)) {
			for (TcbScannedGs1Service.SerializedGs1Data resolvedCoupon : resolvedCoupons) {
				String purchaseRequirementJson = jedis.get(resolvedCoupon.baseGs1);

				if (purchaseRequirementJson == null) {
					continue;
				}

				PurchaseRequirement purchaseRequirement = mapper.readValue(purchaseRequirementJson,
						PurchaseRequirement.class);

				InputCoupon coupon = new InputCoupon();
				coupon.gs1 = resolvedCoupon.gs1;
				coupon.purchaseRequirement = purchaseRequirement;
				coupon.validated = resolvedCoupon.validated;
				coupons.add(coupon);
			}
		}
		return coupons;
	}

}
