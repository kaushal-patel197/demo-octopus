package com.meridian.invoiceworks;

/**
 * A single line on an invoice. Money is a double here (dollars), like most of
 * the codebase. Do not go looking for BigDecimal - there isn't any.
 */
public class LineItem {

    public String description;
    public int quantity;
    public double unitPrice; // dollars

    public LineItem() {
    }

    public LineItem(String description, int quantity, double unitPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // Extended price for this line, before tax and before any discount.
    public double lineTotal() {
        return quantity * unitPrice;
    }
}
