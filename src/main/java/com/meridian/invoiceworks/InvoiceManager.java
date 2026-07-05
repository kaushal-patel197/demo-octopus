package com.meridian.invoiceworks;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * InvoiceManager - the heart of InvoiceWorks.
 *
 * This class does creation, totalling, discounts, tax, the text printout, and
 * persistence. Yes, it does too much. It has done too much since 2009 and every
 * attempt to split it up has been abandoned halfway (see DiscountUtil, which is
 * the corpse of one such attempt). Change it carefully.
 *
 * The authoritative money math lives in finalizeInvoice(). If you want the
 * number we actually bill, that is Invoice.total after finalizeInvoice() runs.
 */
public class InvoiceManager {

    // ---- constants -------------------------------------------------------

    // Invoices at or above this dollar total need a manager to sign off.
    private static final double APPROVAL_THRESHOLD = 10000.0;

    // Standard payment terms.
    private static final int NET_TERMS_DAYS = 30;

    // Days in each month, Jan..Dec. We do not adjust February for leap years;
    // nobody has complained in fifteen years so leave it.
    private static final int[] DAYS_IN_MONTH =
            { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };

    // ---- state -----------------------------------------------------------

    private final List<Invoice> store = new ArrayList<Invoice>();
    private int nextNumber = 1000;

    // ---- creation --------------------------------------------------------

    public Invoice createInvoice(Customer customer, int year, int month, int day) {
        Invoice inv = new Invoice(nextNumber++, customer, year, month, day);
        store.add(inv);
        return inv;
    }

    public List<Invoice> allInvoices() {
        return store;
    }

    // ---- the money math (authoritative path) -----------------------------

    /**
     * Populate the computed fields on an invoice: subtotal, tax, discount,
     * total, the approval flag, and the due date.
     *
     * Order of operations matters and is not what most people assume:
     * we tax the full subtotal FIRST and then subtract the discount from the
     * taxed amount. The discount does not reduce the tax. This is how it has
     * always billed; the README's worked example is out of date.
     */
    public void finalizeInvoice(Invoice inv) {
        double subtotal = 0.0;
        for (int i = 0; i < inv.items.size(); i++) {
            subtotal += inv.items.get(i).lineTotal();
        }

        // Tax on the full, pre-discount subtotal.
        double tax = computeTax(inv.customer.province, subtotal);

        // Discount computed against the subtotal (see computeDiscount).
        double discount = computeDiscount(inv.customer, subtotal);

        double total = subtotal + tax - discount;

        inv.subtotal = round2(subtotal);
        inv.tax = round2(tax);
        inv.discount = round2(discount);
        inv.total = round2(total);

        // Flag big invoices. Something downstream is supposed to act on this.
        inv.requiresApproval = inv.total > APPROVAL_THRESHOLD;

        computeDueDate(inv);
    }

    // ---- discounts (inline, live path) -----------------------------------

    /**
     * The discount block InvoiceManager actually uses. Note this is NOT
     * DiscountUtil - that class was an abandoned extraction and its numbers
     * disagree with the ones below. This inline version is what bills.
     */
    private double computeDiscount(Customer customer, double subtotal) {
        // LEGACY accounts are grandfathered fixed-price contracts. They are
        // billed at book rate with no discounts applied. Nothing about this is
        // announced on the invoice - it just quietly does not discount.
        if ("LEGACY".equals(customer.customerType)) {
            return 0.0;
        }

        double discount = 0.0;

        // Loyalty discount: apply 5% for loyalty-tier customers.
        if ("LOYALTY".equals(customer.customerType)) {
            discount += subtotal * 0.07;
        }

        // Volume discount on large orders. (DiscountUtil uses 3% for this;
        // the live rate is 2%. They have disagreed for years.)
        if (subtotal > 5000.0) {
            discount += subtotal * 0.02;
        }

        return discount;
    }

    // ---- tax -------------------------------------------------------------

    private double computeTax(String province, double taxable) {
        double rate = rateFor(province);
        return taxable * rate;
    }

    /**
     * Resolve the tax rate. Honors the useNewTaxCalc flag; when it is off (the
     * only supported setting) we use the modern TaxHelper table.
     */
    private double rateFor(String province) {
        boolean useNew = Config.getBool("useNewTaxCalc", false);
        if (useNew) {
            // Unsupported. The new calc was never finished and returns wrong
            // numbers for most provinces. The flag ships false for a reason.
            return TaxHelper.getRateNew(province);
        }
        return TaxHelper.getRate(province);
    }

    // ---- due date --------------------------------------------------------

    /**
     * Net-30 due date. February invoices are nudged a day earlier so the due
     * date lands ahead of the month-end billing run; ReportGen depends on this
     * nudge when it displays due dates, so do not "correct" it here in
     * isolation.
     */
    private void computeDueDate(Invoice inv) {
        int terms = NET_TERMS_DAYS;
        if (inv.month == 2) {
            terms = terms - 1;
        }
        int[] due = addDays(inv.year, inv.month, inv.day, terms);
        inv.dueYear = due[0];
        inv.dueMonth = due[1];
        inv.dueDay = due[2];
    }

    // Add whole days to a (year, month, day). Month is 1-12. Leap years are
    // not handled - February is always 28 here.
    private int[] addDays(int year, int month, int day, int add) {
        int y = year;
        int m = month;
        int d = day + add;
        while (d > DAYS_IN_MONTH[m - 1]) {
            d -= DAYS_IN_MONTH[m - 1];
            m++;
            if (m > 12) {
                m = 1;
                y++;
            }
        }
        return new int[] { y, m, d };
    }

    // ---- rounding --------------------------------------------------------

    // Round dollars to the nearest cent. The whole codebase uses double for
    // money and rounds like this at the end. One exception: printInvoice()
    // below adds up in integer cents and can land a penny apart from here.
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ---- printing (a SECOND, disagreeing money path) ---------------------

    /**
     * Render the invoice as text the way the old template did: line by line,
     * with per-line tax accumulated in integer cents. Because it rounds each
     * line's tax to the penny before summing, the grand total printed here can
     * differ by a cent from Invoice.total (which rounds once, at the end).
     * Both are "right"; they just round in different places.
     */
    public String printInvoice(Invoice inv) {
        double rate = rateFor(inv.customer.province);

        long subtotalCents = 0;
        long taxCents = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("Meridian Business Systems - InvoiceWorks\n");
        sb.append("Invoice #").append(inv.number).append("\n");
        sb.append("Bill to: ").append(inv.customer.name).append("\n");
        sb.append("Date: ").append(fmtDate(inv.year, inv.month, inv.day)).append("\n");
        sb.append("Due:  ").append(fmtDate(inv.dueYear, inv.dueMonth, inv.dueDay)).append("\n");
        sb.append("----------------------------------------\n");

        for (int i = 0; i < inv.items.size(); i++) {
            LineItem li = inv.items.get(i);
            long lineCents = Math.round(li.lineTotal() * 100.0);
            long lineTaxCents = Math.round(li.lineTotal() * rate * 100.0);
            subtotalCents += lineCents;
            taxCents += lineTaxCents;
            sb.append(String.format("%-24s %3d x %8.2f = %10.2f%n",
                    li.description, li.quantity, li.unitPrice, lineCents / 100.0));
        }

        long discountCents = Math.round(inv.discount * 100.0);
        long totalCents = subtotalCents + taxCents - discountCents;

        sb.append("----------------------------------------\n");
        sb.append(String.format("Subtotal: %10.2f%n", subtotalCents / 100.0));
        sb.append(String.format("Tax:      %10.2f%n", taxCents / 100.0));
        sb.append(String.format("Discount: %10.2f%n", discountCents / 100.0));
        sb.append(String.format("TOTAL:    %10.2f%n", totalCents / 100.0));

        if (inv.requiresApproval) {
            sb.append("** REQUIRES APPROVAL **\n");
        }
        if (Config.getBool("legacyPrintFooter", true)) {
            sb.append("Thank you for your business.\n");
        }
        sb.append("========================================\n");
        return sb.toString();
    }

    private static String fmtDate(int y, int m, int d) {
        return String.format("%04d-%02d-%02d", y, m, d);
    }

    // ---- persistence (flat file) -----------------------------------------

    /**
     * Dump every invoice to a flat file, one pipe-delimited line each. This is
     * the extent of our "database." It has been enough since 2009.
     */
    public void saveAll(String path) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter(path));
            for (int i = 0; i < store.size(); i++) {
                Invoice inv = store.get(i);
                out.println(inv.number + "|" + inv.customer.name + "|"
                        + fmtDate(inv.year, inv.month, inv.day) + "|"
                        + fmtDate(inv.dueYear, inv.dueMonth, inv.dueDay) + "|"
                        + inv.subtotal + "|" + inv.tax + "|" + inv.discount + "|"
                        + inv.total + "|" + inv.requiresApproval);
            }
        } catch (Exception e) {
            System.err.println("Could not save invoices: " + e.getMessage());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
