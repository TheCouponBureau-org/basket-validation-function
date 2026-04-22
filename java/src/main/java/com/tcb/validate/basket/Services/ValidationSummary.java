package com.tcb.validate.basket.Services;

import java.util.List;

/**
 * =====================================================
 * ValidationSummary
 * =====================================================
 * 
 * This class represents a summary of validation results
 * after processing a batch of records (e.g., basket rows,
 * coupon validations, or test cases).
 * 
 * It is typically used for:
 * - Reporting validation outcomes
 * - Debugging failed records
 * - Displaying summary in dashboards or logs
 */
public class ValidationSummary {

    // Total number of records processed
    private int processed;

    // Number of records that passed validation
    private int passed;

    // Number of records that failed validation
    private int failed;

    // Number of records skipped (not evaluated)
    private int skipped;

    // List of row indices (or IDs) that failed validation
    private List<Integer> failedRows;

    /**
     * Constructor to initialize validation summary
     *
     * @param processed  Total processed records
     * @param passed     Total passed records
     * @param failed     Total failed records
     * @param skipped    Total skipped records
     * @param failedRows List of failed row indices
     */
    public ValidationSummary(
            int processed,
            int passed,
            int failed,
            int skipped,
            List<Integer> failedRows) {

        this.processed = processed;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
        this.failedRows = failedRows;
    }

    /**
     * @return total number of processed records
     */
    public int getProcessed() {
        return processed;
    }

    /**
     * @return number of successfully validated records
     */
    public int getPassed() {
        return passed;
    }

    /**
     * @return number of failed validation records
     */
    public int getFailed() {
        return failed;
    }

    /**
     * @return number of skipped records
     */
    public int getSkipped() {
        return skipped;
    }

    /**
     * @return list of failed row indices (useful for debugging)
     */
    public List<Integer> getFailedRows() {
        return failedRows;
    }
}