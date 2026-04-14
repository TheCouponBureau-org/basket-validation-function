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

### Step 4: Combination Selection

* Build combinations:

       * Primary + Secondary
       * Primary + Third
* Evaluate all valid combinations
* Select the optimal combination based on basket value

### 🔍 Selection Strategy

When multiple combinations are valid:

* Evaluate all combinations
* Compare total basket value
* Select the best-performing combination

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


## 5. Example Use Case

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

* Coupon requires **minimum 2 items** from primary group
* Basket contains **2 valid items**

```text
Primary Purchase Requirement = 2 units (Satisfied)
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

* Coupon applied successfully
* Discount: **100 cents ($1.00)**
* Consumed items:

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