package com.meridian.invoiceworks;

import java.util.ArrayList;
import java.util.List;

/**
 * An invoice. Anemic model - public fields, no behavior worth mentioning.
 * The numbers below (subtotal, discount, tax, total) are populated by
 * InvoiceManager.finalizeInvoice(); do not trust them until then.
 */
public class Invoice {

    public int number;
    public Customer customer;
    public List<LineItem> items = new ArrayList<LineItem>();

    // Invoice date, stored as three ints. Yes, really. Predates us using Date.
    public int year;
    public int month; // 1-12
    public int day;

    // Due date, filled in by InvoiceManager. Also three ints.
    public int dueYear;
    public int dueMonth;
    public int dueDay;

    // Computed money fields (dollars, double). Populated by finalizeInvoice().
    public double subtotal;
    public double discount;
    public double tax;
    public double total;

    // Set by InvoiceManager when total crosses the approval threshold.
    // Read by exactly one place in the codebase.
    public boolean requiresApproval;

    public Invoice() {
    }

    public Invoice(int number, Customer customer, int year, int month, int day) {
        this.number = number;
        this.customer = customer;
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public void addItem(String description, int quantity, double unitPrice) {
        items.add(new LineItem(description, quantity, unitPrice));
    }
}
