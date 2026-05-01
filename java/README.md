# Basket Validation Engine – Technical Documentation

## 1. Objective

The Basket Validation Engine is designed to evaluate a given shopping basket against predefined coupon rules and determine:

* Applicable coupons
* Discount values
* Consumed basket items

The implementation follows the defined business logic and supports both **single** and **multi-coupon scenarios** in a consistent, scalable, and efficient manner.

## 2. High-Level Architecture

The validation process is structured into the following stages:

1. Basket normalization (merging duplicate items)
2. Coupon iteration
3. Purchase requirement validation
4. Combination selection
5. Basket reduction (item consumption)
6. Discount calculation
7. Result aggregation

This structured approach ensures clarity, maintainability, and extensibility.

## 3. Data Structures

### 3.1 Basket Item

Each basket item contains:

* `product_code` (GTIN/EAN)
* `price`
* `quantity`
* `unit`

### 3.2 Coupon Structure

Each coupon includes:

* `gs1` → Unique coupon identifier
* `base_gs1` → Base reference identifier
* `purchase_requirement` → Defines conditions required for coupon application

### 3.3 Purchase Requirement Object

Defines rules for coupon eligibility, including:

* Primary purchase conditions
* Secondary purchase conditions
* Third purchase conditions
* Rule configuration codes
* Discount configuration


## 4. Core Processing Flow

### Step 1: Basket Preparation

* Merge duplicate items
* Normalize data for processing

### Step 2: Coupon Iteration

* Process coupons sequentially
* Update basket after each coupon

### Step 3: Requirement Validation

* Validate primary conditions
* Validate secondary and/or third conditions

### Step 5: Basket Reduction

* Consume required quantities only
* Prevent reuse of consumed items
* Maintain updated basket

### Step 6: Discount Calculation

* Based on:

    * `save_value_code`
    * Eligible basket items

### Step 7: Output Formation

* Append applied coupon details
* Aggregate total discount


## 5. Example Scenario

### Input Basket

```json
{
  "basket": [
    {
      "product_code": "037000930396",
      "price": 1.29,
      "quantity": 1,
      "unit": "item"
    },
    {
      "product_code": "037000934677",
      "price": 1.34,
      "quantity": 1,
      "unit": "item"
    }
  ],
  "coupons": [
    {
      "gs1": "8112009988459000019133924009755364",
      "purchase_requirement": {
        "primary_purchase_gtins": [
          "037000930396",
          "037000934677",
          "012345678912"
        ],
        "primary_purchase_eans": [
          "7106919588011",
          "8952803493171",
          "5012345678900"
        ],
        "primary_purchase_save_value": 100,
        "primary_purchase_requirements": 2,
        "primary_purchase_req_code": 0,
        "save_value_code": 0
      },
      "base_gs1": "811200998845900001"
    }
  ]
}
```

### Scenario Explanation

* The coupon requires at least 2 qualifying items from the primary purchase group
* The basket contains 2 matching items (product_code matches the primary GTIN list)

```text
Primary Purchase Requirement = 2 units → ✔ Satisfied
```
### Processing Details

* Basket is normalized
* Coupon is evaluated against the basket
* Primary purchase condition is successfully satisfied

Discount is calculated using:

```text
save_value_code = 0 → Fixed discount
primary_purchase_save_value = 100 cents ($1.00)
```

### Output Explanation

The response is returned in a structured format containing:

1. Total Discount
discount_in_cents → Total savings applied to the basket

2. Applied Coupons

Each applied coupon includes:

```text
coupon_code
face_value_in_cents
product_codes → Items used for validation
```

### Output

```json
{
  "discount_in_cents": 100,
  "applied_coupons": [
    {
      "coupon_code": "8112009988459000019133924009755364",
      "face_value_in_cents": 100,
      "product_codes": {
        "gtins": [
          "037000930396",
          "037000934677"
        ]
      }
    }
  ]
}
```

### Result Summary

* Coupon successfully validated and applied
* Discount: **100 cents ($1.00)**
* Items consumed for validation:

    * 037000930396
    * 037000934677


## 6. Test Execution Guide

### 6.1 Prerequisites

Ensure the following:

* Java 8 or higher
* Maven 3.6+
* Dependencies installed
* Required files in project root:

    * `POS Basket Validation Use Cases - Final.xlsx`
    * `input.json`


### 6.2 Running Test Suites

#### ▶ Run All Test Cases

```bash
mvn clean test
```

#### ▶ Run Single Coupon Validation Tests

```bash
mvn clean test -Dtest=ExcelValidationTest
```

#### ▶ Run Multi-Coupon Validation Tests

```bash
mvn clean test -Dtest=MultiCouponValidationTest
```

#### ▶ Run Single Test

```bash
mvn clean test -Dtest=ValidateBasketTest
```
## 7. Components Overview

The project is organized into core processing components, supporting services, and test utilities. Each component plays a specific role in the basket validation workflow.

### Core Components

- **BasketValidator**  
  The central processing unit responsible for:
    - Evaluating the basket  
    - Applying eligible coupons  
    - Producing the final discount and applied coupon details  


- **PurchaseFactory**  
  Converts raw coupon input data into structured domain objects, enabling efficient evaluation of purchase requirements.


- **BasketHelper**  
  Provides reusable utility methods for:
    - Merging duplicate basket items  
    - Filtering eligible products  
    - Performing basket-level calculations  

  This helps keep the core logic clean and maintainable.


- **BasketValidationResults**  
  Defines all data structures used across the system, including:
    - Input representation  
    - Intermediate processing data  
    - Final output response  


### Services Layer

- **BasketReducerService**  
  Handles basket updates by:
    - Consuming items used during coupon application  
    - Preventing reuse of already-applied items  


- **DiscountService**  
  Responsible for calculating discounts based on:
    - Coupon configuration (`save_value_code`)  
    - Pricing and applicable rules  


- **RequirementService**  
  Validates coupon applicability by:
    - Evaluating purchase conditions  
    - Handling rule combinations (primary, secondary, etc.)  


- **ValidationSummary**  
  Generates a summary of validation execution, including:
    - Passed cases  
    - Failed cases  
    - Skipped scenarios  


### Test Runners

- **ExcelValidationRunner**  
  Executes Excel-based test scenarios to:
    - Validate coupon logic at scale  
    - Compare expected vs actual results  


- **MultiCouponExcelValidationRunner**  
  Handles complex scenarios involving multiple coupons, ensuring:
    - Correct interaction between coupons  
    - Real-world validation behavior  


### Test Classes

- **ValidateBasketTest**  
  Validates a single basket input (JSON-based), useful for:
    - Quick debugging  
    - Functional verification  


- **ExcelValidationTest**  
  Executes all Excel-based single coupon scenarios to ensure consistent behavior across cases.


- **MultiCouponValidationTest**  
  Validates multi-coupon scenarios, ensuring correct interaction and application logic.
