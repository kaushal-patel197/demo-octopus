package com.meridian.invoiceworks;

/**
 * Runs a sample day at Meridian: a few customers, a few invoices, the printout,
 * and a month-end A/R summary. This is also the closest thing we have to a
 * regression test - eyeball the numbers after a change.
 */
public class Main {

    public static void main(String[] args) {
        InvoiceManager mgr = new InvoiceManager();

        Customer acme = new Customer(1, "Acme Corp", "REGULAR", 8, "ON");
        Customer bluebird = new Customer(2, "Bluebird Ltd", "LOYALTY", 5, "ON");
        Customer oldco = new Customer(3, "Old Holdings", "LEGACY", 16, "ON");
        Customer quebec = new Customer(4, "Quebec Foods", "REGULAR", 3, "QC");
        Customer bigwig = new Customer(5, "Bigwig Industries", "LOYALTY", 9, "ON");

        // Invoice 1: the mixed-money case. Two identical $10.10 lines in Ontario.
        // getTotal() rounds once; the printout sums integer cents per line. They
        // land a penny apart.
        Invoice inv1 = mgr.createInvoice(acme, 2024, 1, 15);
        inv1.addItem("Widget", 1, 10.10);
        inv1.addItem("Gadget", 1, 10.10);
        mgr.finalizeInvoice(inv1);

        // Invoice 2: a loyalty customer. Watch the discount - the code applies
        // 7%, not the 5% the comment (and the handbook) claim.
        Invoice inv2 = mgr.createInvoice(bluebird, 2024, 1, 20);
        inv2.addItem("Consulting (hrs)", 10, 200.00);
        mgr.finalizeInvoice(inv2);

        // Invoice 3: a LEGACY account over the volume threshold. A REGULAR
        // customer would get 2% off here; LEGACY silently gets nothing.
        Invoice inv3 = mgr.createInvoice(oldco, 2024, 1, 22);
        inv3.addItem("Annual license", 60, 100.00);
        mgr.finalizeInvoice(inv3);

        // Invoice 4: Quebec. Its rate comes out of the "dead" LegacyTaxTable.
        Invoice inv4 = mgr.createInvoice(quebec, 2024, 3, 10);
        inv4.addItem("Maintenance", 10, 100.00);
        mgr.finalizeInvoice(inv4);

        // Invoice 5: crosses the $10,000 approval threshold. It gets flagged,
        // and ReportGen will quietly drop it from the A/R summary.
        Invoice inv5 = mgr.createInvoice(bigwig, 2024, 1, 25);
        inv5.addItem("Enterprise rollout", 50, 250.00);
        mgr.finalizeInvoice(inv5);

        // Invoice 6: a February invoice. The stored due date is a day early on
        // purpose; the report adds it back.
        Invoice inv6 = mgr.createInvoice(acme, 2024, 2, 27);
        inv6.addItem("Support retainer", 1, 100.00);
        mgr.finalizeInvoice(inv6);

        // Print every invoice.
        Invoice[] all = { inv1, inv2, inv3, inv4, inv5, inv6 };
        for (int i = 0; i < all.length; i++) {
            System.out.print(mgr.printInvoice(all[i]));
            System.out.println();
        }

        // Highlight the penny divergence on invoice 1.
        System.out.println("--- mixed-money check (invoice #" + inv1.number + ") ---");
        System.out.printf("getTotal (double, rounded once): %.2f%n", inv1.total);
        System.out.println("printed total (integer cents):   see TOTAL line above");
        System.out.println();

        // Month-end A/R summary (drops the flagged invoice #" + inv5.number).
        ReportGen report = new ReportGen();
        System.out.print(report.arSummary(mgr.allInvoices()));

        // Persist the "database."
        mgr.saveAll("invoices.dat");
    }
}
