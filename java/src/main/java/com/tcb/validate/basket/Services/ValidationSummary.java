package com.tcb.validate.basket.Services;

import java.util.List;

public class ValidationSummary {

    private int processed;
    private int passed;
    private int failed;
    private int skipped;
    private List<Integer> failedRows;

    public ValidationSummary(int processed, int passed, int failed, int skipped, List<Integer> failedRows) {
        this.processed = processed;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
        this.failedRows = failedRows;
    }

    public int getProcessed() { return processed; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public int getSkipped() { return skipped; }
    public List<Integer> getFailedRows() { return failedRows; }
}