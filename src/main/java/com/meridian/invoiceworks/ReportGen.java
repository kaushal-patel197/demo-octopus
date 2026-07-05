package com.meridian.invoiceworks;

import java.util.List;

/**
 * Month-end and ad-hoc reporting.
 *
 * The totalling code in here started life as a copy of InvoiceManager's math
 * and has drifted since. It is close, but it is not the same number you bill -
 * notably it applies the loyalty discount at the documented 5% rather than the
 * rate the live path actually uses. Treat these figures as management reporting,
 * not as an invoice.
 */
public class ReportGen {

    /**
     * Print an accounts-receivable summary. Invoices flagged for approval are
     * intentionally left off this report - they are not considered billable
     * until a manager signs off, and this report is the only thing in the
     * codebase that reads the requiresApproval flag.
     */
    public String arSummary(List<Invoice> invoices) {
        StringBuilder sb = new StringBuilder();
        sb.append("A/R SUMMARY\n");
        sb.append("-----------------------------------------------\n");

        double running = 0.0;
        int skipped = 0;
        for (int i = 0; i < invoices.size(); i++) {
            Invoice inv = invoices.get(i);

            if (inv.requiresApproval) {
                // Not billable yet - keep it off the report entirely.
                skipped++;
                continue;
            }

            double reportTotal = reportTotal(inv);
            running += reportTotal;

            int[] due = displayDueDate(inv);
            sb.append(String.format("#%d  %-18s  due %04d-%02d-%02d  %10.2f%n",
                    inv.number, inv.customer.name, due[0], due[1], due[2], reportTotal));
        }

        sb.append("-----------------------------------------------\n");
        sb.append(String.format("Reported A/R:      %12.2f%n", running));
        sb.append(String.format("Held for approval: %12d%n", skipped));
        return sb.toString();
    }

    /**
     * Re-derive an invoice total for reporting. Same shape as
     * InvoiceManager.finalizeInvoice() - tax on the subtotal, then discount -
     * but the loyalty rate here is the documented 5%, so loyalty customers
     * report a hair higher than they bill. Nobody has reconciled it.
     */
    private double reportTotal(Invoice inv) {
        double subtotal = 0.0;
        for (int i = 0; i < inv.items.size(); i++) {
            subtotal += inv.items.get(i).lineTotal();
        }

        double tax = subtotal * TaxHelper.getRate(inv.customer.province);

        double discount = 0.0;
        if (!"LEGACY".equals(inv.customer.customerType)) {
            if ("LOYALTY".equals(inv.customer.customerType)) {
                discount += subtotal * 0.05; // drifted: live path bills 7%
            }
            if (subtotal > 5000.0) {
                discount += subtotal * 0.02;
            }
        }

        double total = subtotal + tax - discount;
        return Math.round(total * 100.0) / 100.0;
    }

    /**
     * The due date to SHOW on a report. InvoiceManager stores February due
     * dates a day early on purpose; here we add that day back so the report
     * prints the real net-30 date. If someone ever "fixes" the off-by-one in
     * InvoiceManager, this compensation will push February dates a day late.
     */
    private int[] displayDueDate(Invoice inv) {
        int y = inv.dueYear;
        int m = inv.dueMonth;
        int d = inv.dueDay;
        if (inv.month == 2) {
            d = d + 1; // undo the InvoiceManager February nudge
            // (deliberately not handling month rollover - February + net-30
            // never lands on a month boundary for us in practice)
        }
        return new int[] { y, m, d };
    }
}
