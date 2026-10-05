package org.thecouponbureau.validate.basket.Services;

/** Shared routing and retailer identity rules for TCB operations. */
final class TcbMode {
    private TcbMode() {
    }

    static String normalize(String mode) {
        if (mode == null || mode.trim().isEmpty() || "retailer".equalsIgnoreCase(mode.trim())) {
            return "retailer";
        }
        if ("accelerator".equalsIgnoreCase(mode.trim())) {
            return "accelerator";
        }
        throw new IllegalArgumentException("TCB mode must be retailer or accelerator.");
    }

    static String retailerEmailDomain(String mode, String retailerEmailDomain) {
        if (!"accelerator".equals(mode)) {
            return null;
        }
        if (retailerEmailDomain == null || retailerEmailDomain.trim().isEmpty()) {
            throw new IllegalArgumentException("Accelerator redemption requires retailer_email_domain.");
        }
        return retailerEmailDomain;
    }
}
