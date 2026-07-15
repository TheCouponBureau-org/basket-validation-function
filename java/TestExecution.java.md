# Basket Validator

A Java-based Basket Validation tool that validates coupon redemption scenarios against input baskets and generates validation reports.

## Features

- Validate basket data using Excel test cases
- Validate a single JSON basket file
- JUnit 5 test suite
- Test execution using JUnit Tags
- HTML Test Report
- JUnit XML Report
- JaCoCo Code Coverage Report
- Executable Fat JAR

---

# Requirements

- Java 17+ (or your project version)
- Maven 3.8+

Verify installation:

```bash
java -version
mvn -version
```

---

# Building the Project

Clean and build the project:

```bash
mvn clean package
```

Clean and build the project with test skip:

```bash
mvn clean package -DskipTests
```

This generates:

```
target/
├── basket-validator-1.0-SNAPSHOT.jar
└── basket-validator-1.0-SNAPSHOT-all.jar
```

The executable jar is:

```
basket-validator-1.0-SNAPSHOT-all.jar
```

---

# Running Tests

## Run all tests

```bash
mvn clean test
```

---

## Run Local Basket Validation Tests

```bash
mvn clean test -Dtags=localBasketValidation
```

---

## Run Basket Validation Tests

```bash
mvn clean test -Dtags=validateBasket
```

---

## Run Single JSON Validation

```bash
mvn clean test -Dtags=single-json
```

---

# Test Classes

```
BasketValidationServiceTest
```

Contains the following test methods:

| Test Method | Tag | Description |
|-------------|-----|-------------|
| localBasketValidation | localBasketValidation | Executes Excel-based local validation |
| validateBasket | validateBasket | Executes API basket validation |
| validateSingleJson | single-json | Validates a single JSON input |

---

# Running the Executable Jar

Example:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT-all.jar
```

Example:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT-all.jar input-gs1-only.json
```

---

# Reports

## JUnit XML Report

Generated automatically after running tests.

Location:

```
target/surefire-reports/
```

Example:

```
TEST-org.thecouponbureau.validate.basket.BasketValidationServiceTest.xml
```

---

## HTML Test Report

Generate report:

```bash
mvn surefire-report:report
```

Location:

```
target/reports/surefire.html
```

The report includes:

- Total Tests
- Passed
- Failed
- Skipped
- Execution Time

---

## Code Coverage Report

Generated automatically when tests run.

Location:

```
target/site/jacoco/index.html
```

The report includes:

- Instruction Coverage
- Branch Coverage
- Line Coverage
- Method Coverage
- Class Coverage

---

# Maven Commands

## Clean

```bash
mvn clean
```

## Compile

```bash
mvn compile
```

## Run Tests

```bash
mvn test
```

## Package

```bash
mvn package
```

## Generate HTML Test Report

```bash
mvn surefire-report:report
```

## Generate All Reports

```bash
mvn clean test surefire-report:report
```

---

# Output Directories

```
target/

├── classes/
├── test-classes/
├── reports/
	├── surefire.html
├── site/
│   └── jacoco/
│       ├── index.html
│       ├── jacoco.xml
│       └── jacoco.csv
└── basket-validator-1.0-SNAPSHOT-all.jar
```

---

# Technologies Used

- Java
- Maven
- JUnit 5
- Apache POI
- Jackson
- Log4j2
- JaCoCo
- Maven Surefire
- Maven Shade Plugin

---

# Troubleshooting

## No tests executed

Run:

```bash
mvn clean test
```

or verify the tag name:

```bash
mvn clean test -Dtags=validateBasket
```

---

## No main manifest attribute

Use the executable shaded jar:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT-all.jar
```

Do not run:

```bash
java -jar target/basket-validator-1.0-SNAPSHOT.jar
```

---

## HTML report not found

Generate it:

```bash
mvn surefire-report:report
```

Then open:

```
target/reports/surefire.html
```

---

## Code coverage report not found

Run:

```bash
mvn clean test
```

Then open:

```
target/site/jacoco/index.html
```

---

# License

Internal project for The Coupon Bureau Basket Validation.
