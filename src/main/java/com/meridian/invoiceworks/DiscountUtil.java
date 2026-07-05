package com.meridian.invoiceworks;

/**
 * Discount calculations.
 *
 * Despite the name, InvoiceManager does NOT call into this class for the totals
 * it actually bills - it has its own inline discount block. This was extracted
 * during a 2014 refactor that was never wired up. The numbers here disagree
 * with the live path (see loyalty rate below). Kept because ReportGen's author
 * once mentioned wanting to reuse it. Nobody has.
 */
public class DiscountUtil {

    // Loyalty discount for LOYALTY-type customers.
    public static double loyaltyDiscount(double subtotal) {
        // 5% - this is the number the handbook documents.
        return subtotal * 0.05;
    }

    // Volume discount for large invoices.
    public static double volumeDiscount(double subtotal) {
        if (subtotal > 5000.0) {
            return subtotal * 0.03;
        }
        return 0.0;
    }

    // Combined discount the way the 2014 refactor intended it to work.
    public static double totalDiscount(Customer customer, double subtotal) {
        double d = 0.0;
        if ("LOYALTY".equals(customer.customerType)) {
            d += loyaltyDiscount(subtotal);
        }
        d += volumeDiscount(subtotal);
        return d;
    }
}
