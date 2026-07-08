package org.thecouponbureau.validate.basket.runners;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.FileInputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.thecouponbureau.validate.basket.Services.TcbCouponRedeemService;
import org.thecouponbureau.validate.basket.core.BasketValidator;
import org.thecouponbureau.validate.basket.model.basketValidationResults.AppliedCoupon;
import org.thecouponbureau.validate.basket.model.basketValidationResults.BasketValidationInput;
import org.thecouponbureau.validate.basket.model.basketValidationResults.ValidationResult;
import org.thecouponbureau.validate.basket.Services.TcbCouponRollbackService;

public class BasketValidationServiceRunner {

	 private static final Logger logger =
	            LogManager.getLogger(BasketValidationServiceRunner.class);
	private void setTcbConfiguration(BasketValidationInput input) {

		input.tcbBaseUrl = "https://api.try.thecouponbureau.org/";
		input.tcbAccessKey = "8053fd0f80cf3778659def1359cac218";
		input.tcbSecretKey = "eb42623aa2675e50f15da4f6d4aa0ad6";

	}

	public void validateJsonFile(String jsonFile) throws Exception {

		String jsonPath =
				System.getProperty("user.dir")
				+ "/" + jsonFile;

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String inputJson = Files.readString(Paths.get(jsonPath));

		BasketValidationInput input =
				mapper.readValue(inputJson, BasketValidationInput.class);

		setTcbConfiguration(input);

		ValidationResult result =
				BasketValidator.validateBasketHelper(input);

		logger.info(
				mapper.writerWithDefaultPrettyPrinter()
				.writeValueAsString(result));

		List<String> gs1List = new ArrayList<>();

		for (AppliedCoupon coupon :
			result.basketValidationOutput.appliedCoupons) {

			gs1List.add(coupon.couponCode);

		}

		if (!gs1List.isEmpty()) {

			String redeemResponse = null;

			try {

				logger.info("====================================");
				logger.info("Redeeming Coupons");
				logger.info("====================================");
				logger.info("Coupons: {}", gs1List);

				redeemResponse =
						TcbCouponRedeemService.redeemCoupons(
								input.tcbBaseUrl,
								input.tcbAccessKey,
								input.tcbSecretKey,
								gs1List);
				
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

							rollbackGs1List.add(
									coupon.get("gs1").asText());

						}
					}
					
					logger.info("====================================");
					logger.info("Rolling Back Coupons");
					logger.info("====================================");
					logger.info(rollbackGs1List);

					Map<String, String> rollbackResponses =
							TcbCouponRollbackService.rollbackCoupons(
									input.tcbBaseUrl,
									input.tcbAccessKey,
									input.tcbSecretKey,
									rollbackGs1List);
					
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

	public void validateApproach1(String excelFile) throws Exception {

		logger.info("====================================");
		logger.info("Executing Approach_1");
		logger.info("====================================");

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String excelPath =
				System.getProperty("user.dir")
				+ "/" + excelFile;

		Workbook workbook =
				new XSSFWorkbook(new FileInputStream(excelPath));

		Sheet sheet = workbook.getSheet("Approch_1");

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

			if (row.getCell(5) == null
					|| row.getCell(5).toString().trim().isEmpty()) {
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

			BasketValidationInput input =
					mapper.readValue(inputJson, BasketValidationInput.class);

			setTcbConfiguration(input);

			ValidationResult actualResult =
					BasketValidator.validateBasketHelper(input);

			String actualJson =
					mapper.writerWithDefaultPrettyPrinter()
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

	public void validateApproach2(String excelFile) throws Exception {

		logger.info("====================================");
		logger.info("Executing Approach_2");
		logger.info("====================================");

		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

		String excelPath =
				System.getProperty("user.dir")
				+ "/" + excelFile;

		Workbook workbook =
				new XSSFWorkbook(new FileInputStream(excelPath));

		Sheet sheet = workbook.getSheet("Approch_2");

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

			if (row.getCell(5) == null
					|| row.getCell(5).toString().trim().isEmpty()) {
				skipped++;
				continue;
			}

			String scenario = "";

			if (row.getCell(0) != null) {
				scenario = row.getCell(0).toString().trim();
			}

			String inputJson =
					row.getCell(5).getStringCellValue().trim();
			
			String expectedJson = "";

			if (row.getCell(7) != null) {
			    expectedJson = row.getCell(7).getStringCellValue().trim();
			}

			processed++;

			try {

				BasketValidationInput input =
						mapper.readValue(inputJson,
								BasketValidationInput.class);

				setTcbConfiguration(input);

				ValidationResult result =
						BasketValidator.validateBasketHelper(input);
				String actualJson =
				        mapper.writerWithDefaultPrettyPrinter()
				                .writeValueAsString(result.basketValidationOutput);

				JsonNode expectedNode = mapper.readTree(expectedJson);
				JsonNode actualNode = mapper.readTree(actualJson);

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

				for (AppliedCoupon coupon :
					result.basketValidationOutput.appliedCoupons) {

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

				    	String redeemResponse =
				                TcbCouponRedeemService.redeemCoupons(
				                        input.tcbBaseUrl,
				                        input.tcbAccessKey,
				                        input.tcbSecretKey,
				                        gs1List);
				        
				    	logger.info("");
				        logger.info("Redeem Response:");
				        logger.info(redeemResponse);

				        JsonNode redeemNode = mapper.readTree(redeemResponse);

				        JsonNode newlyRedeemed = redeemNode.get("newly_redeemed");

				        if (newlyRedeemed != null) {

				            for (JsonNode coupon : newlyRedeemed) {

				                rollbackGs1List.add(
				                        coupon.get("gs1").asText());

				            }

				        }

				    } finally {

				        if (!rollbackGs1List.isEmpty()) {

				        	logger.info("");
				            logger.info("Rolling Back Coupons:");
				            logger.info(rollbackGs1List);

				            Map<String, String> rollbackResponses =
				                    TcbCouponRollbackService.rollbackCoupons(
				                            input.tcbBaseUrl,
				                            input.tcbAccessKey,
				                            input.tcbSecretKey,
				                            rollbackGs1List);

				            
				            logger.info("Rollback Response:");

				            for (Map.Entry<String, String> entry :
				                    rollbackResponses.entrySet()) {

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
			}
			catch (Exception e) {

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
		
		logger.info("Failed Rows:");
		logger.info(failedRows);
	}

	public void rollbackCouponsFromFile(String fileName) throws Exception {

		String filePath =
				System.getProperty("user.dir")
				+ "/" + fileName;

		List<String> gs1List =
				Files.readAllLines(Paths.get(filePath));

		gs1List.removeIf(String::isBlank);

		if (gs1List.isEmpty()) {

			logger.info("No GS1s found.");
			return;
		}

		BasketValidationInput input = new BasketValidationInput();
		setTcbConfiguration(input);

		int success = 0;
		int failed = 0;

		List<String> failedCoupons = new ArrayList<>();

		BufferedWriter writer =
				new BufferedWriter(
						new FileWriter("rollback-failed.txt"));
		
		logger.info("====================================");
		logger.info("Rollback Started");
		logger.info("====================================");

		for (String gs1 : gs1List) {

			try {

				List<String> singleCoupon = new ArrayList<>();
				singleCoupon.add(gs1);

				Map<String, String> rollbackResponses =
						TcbCouponRollbackService.rollbackCoupons(
								input.tcbBaseUrl,
								input.tcbAccessKey,
								input.tcbSecretKey,
								singleCoupon);

				logger.info("----------------------------------------");
				logger.info("✅ SUCCESS : " + gs1);

				for (Map.Entry<String, String> entry : rollbackResponses.entrySet()) {

					logger.info(entry.getValue());

				}
				success++;

			} catch (Exception e) {

				failed++;
				failedCoupons.add(gs1);

				writer.write(gs1);
				writer.newLine();

				logger.info("----------------------------------------");
				logger.info("❌ FAILED : " + gs1);
				logger.info(e.getMessage());

			}

		}

		writer.close();

		
		logger.info("====================================");
		logger.info("Rollback Summary");
		logger.info("====================================");

		
		logger.info("Total   : " + gs1List.size());
		logger.info("Success : " + success);
		logger.info("Failed  : " + failed);

		if (!failedCoupons.isEmpty()) {
			
			logger.info("Failed Coupons:");

			for (String gs1 : failedCoupons) {
				logger.info(gs1);
			}
		
			logger.info("rollback-failed.txt created.");

		} else {
			
			logger.info("All coupons rolled back successfully.");

		}
	}


}