package com.tcb.validate.basket.runners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.tcb.validate.basket.ValidateBasket;
import com.tcb.validate.basket.Services.ValidationSummary;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MultiCouponExcelValidationRunner {

    public ValidationSummary runValidation() {

        String filePath = "POS Basket Validation Use Cases - Final.xlsx";

        Workbook workbook = null;

        int processed = 0, skipped = 0, passed = 0, failed = 0;
        List<Integer> failedRows = new ArrayList<>();

        try {
            FileInputStream fis = new FileInputStream(filePath);
            workbook = new XSSFWorkbook(fis);
            fis.close();

            Sheet sheet = workbook.getSheet("Multi-Coupon Validation Cases");

            if (sheet == null) {
                throw new RuntimeException("❌ Sheet 'Multi-Coupon Validation Cases' not found!");
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            Row headerRow = sheet.getRow(0);

            int inputCol = -1, expectedCol = -1;

            // 🔍 Find columns
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim();

                if ("Basket Validation Input".equalsIgnoreCase(header)) {
                    inputCol = cell.getColumnIndex();
                } else if ("Basket Validation Output".equalsIgnoreCase(header)) {
                    expectedCol = cell.getColumnIndex();
                }
            }

            // 🚨 Column validation
            if (inputCol == -1 || expectedCol == -1) {
                throw new RuntimeException("❌ Required columns not found!");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null || isRowEmpty(row)) {
                    skipped++;
                    continue;
                }

                try {
                    Cell inputCell = row.getCell(inputCol);
                    Cell expectedCell = row.getCell(expectedCol);

                    // 🔐 Safe cell handling
                    if (inputCell == null || expectedCell == null ||
                            inputCell.getCellType() == CellType.BLANK ||
                            expectedCell.getCellType() == CellType.BLANK) {

                        skipped++;
                        continue;
                    }

                    String inputJson = inputCell.getStringCellValue().trim();
                    String expectedJson = expectedCell.getStringCellValue().trim();

                    System.out.println("\n➡️ Processing row: " + i);

                    // 🔄 Deserialize input
                    ValidateBasket.BasketValidationInput input =
                            mapper.readValue(inputJson, ValidateBasket.BasketValidationInput.class);

                    // 🔥 Run validation logic
                    ValidateBasket.ValidationResult result =
                            ValidateBasket.validateBasketHelper(input);

                    String actualJson = mapper.writeValueAsString(result);

                    // 🔍 Compare only required node
                    JsonNode actualRoot = mapper.readTree(actualJson);
                    JsonNode expectedRoot = mapper.readTree(expectedJson);

                    JsonNode actualNode = actualRoot.get("basket_validation_output");
                    JsonNode expectedNode = expectedRoot.has("basket_validation_output")
                            ? expectedRoot.get("basket_validation_output")
                            : expectedRoot;

                    boolean isMatch = actualNode != null && actualNode.equals(expectedNode);

                    if (isMatch) {
                        passed++;
                        System.out.println("✔ PASS");
                    } else {
                        failed++;
                        failedRows.add(i);

                        System.out.println("✘ FAIL row: " + i);
                        System.out.println("Expected: " + expectedNode);
                        System.out.println("Actual  : " + actualNode);
                    }

                    processed++;

                } catch (Exception e) {
                    failed++;
                    failedRows.add(i);

                    System.out.println("⚠ ERROR at row: " + i);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (workbook != null) workbook.close();
            } catch (Exception ignored) {}
        }

        if (!failedRows.isEmpty()) {
            System.out.println("✘ Failed Rows: " + failedRows);
        }

        return new ValidationSummary(processed, passed, failed, skipped, failedRows);
    }

    // 🔥 Helper to check empty row
    private static boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}