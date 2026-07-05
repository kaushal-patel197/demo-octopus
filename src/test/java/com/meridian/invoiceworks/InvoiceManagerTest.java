package com.meridian.invoiceworks;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for InvoiceManager, focusing on discount and tax calculations.
 */
public class InvoiceManagerTest {

    private static final double DELTA = 0.001;

    // Helper: build a finalized invoice with a single line item.
    private Invoice makeInvoice(Customer customer, double unitPrice) {
        InvoiceManager mgr = new InvoiceManager();
        Invoice inv = mgr.createInvoice(customer, 2024, 6, 1);
        inv.addItem("Item", 1, unitPrice);
        mgr.finalizeInvoice(inv);
        return inv;
    }

    // ---- loyalty discount (>2 years) ------------------------------------

    @Test
    public void regularCustomerOver2Years_gets10PercentLoyaltyDiscount() {
        Customer c = new Customer(1, "Test", "REGULAR", 3, "AB"); // AB = 5% tax
        Invoice inv = makeInvoice(c, 100.00);
        // subtotal = 100, discount = 10% of 100 = 10
        assertEquals(10.00, inv.discount, DELTA);
    }

    @Test
    public void regularCustomerExactly2Years_getsNoLoyaltyDiscount() {
        Customer c = new Customer(1, "Test", "REGULAR", 2, "AB");
        Invoice inv = makeInvoice(c, 100.00);
        // yearsWithUs == 2 is NOT > 2, so no discount
        assertEquals(0.00, inv.discount, DELTA);
    }

    @Test
    public void regularCustomerUnder2Years_getsNoDiscount() {
        Customer c = new Customer(1, "Test", "REGULAR", 1, "AB");
        Invoice inv = makeInvoice(c, 100.00);
        assertEquals(0.00, inv.discount, DELTA);
    }

    @Test
    public void loyaltyCustomerOver2Years_gets10PercentDiscount() {
        // LOYALTY type with >2 years gets the 10% years-based rate (not 7%).
        Customer c = new Customer(1, "Test", "LOYALTY", 5, "AB");
        Invoice inv = makeInvoice(c, 100.00);
        assertEquals(10.00, inv.discount, DELTA);
    }

    @Test
    public void loyaltyCustomerUnder2Years_gets7PercentDiscount() {
        // LOYALTY type with <=2 years falls back to 7% type-based rate.
        Customer c = new Customer(1, "Test", "LOYALTY", 1, "AB");
        Invoice inv = makeInvoice(c, 100.00);
        assertEquals(7.00, inv.discount, DELTA);
    }

    // ---- LEGACY customers still get no discount -------------------------

    @Test
    public void legacyCustomerOver2Years_getsNoDiscount() {
        Customer c = new Customer(1, "Test", "LEGACY", 16, "AB");
        Invoice inv = makeInvoice(c, 100.00);
        assertEquals(0.00, inv.discount, DELTA);
    }

    // ---- volume discount stacks with loyalty ----------------------------

    @Test
    public void longStandingCustomerOverVolumeThreshold_getsBothDiscounts() {
        // subtotal = 6000, loyalty = 10% = 600, volume = 2% = 120 → total discount = 720
        Customer c = new Customer(1, "Test", "REGULAR", 5, "AB");
        Invoice inv = makeInvoice(c, 6000.00);
        assertEquals(720.00, inv.discount, DELTA);
    }

    @Test
    public void newCustomerOverVolumeThreshold_getsOnlyVolumeDiscount() {
        // subtotal = 6000, no loyalty (0 years), volume = 2% = 120
        Customer c = new Customer(1, "Test", "REGULAR", 0, "AB");
        Invoice inv = makeInvoice(c, 6000.00);
        assertEquals(120.00, inv.discount, DELTA);
    }

    // ---- total calculation is correct -----------------------------------

    @Test
    public void totalIsSubtotalPlusTaxMinusDiscount() {
        // AB = 5% tax, REGULAR with 3 years → 10% loyalty
        // subtotal=200, tax=10, discount=20, total=190
        Customer c = new Customer(1, "Test", "REGULAR", 3, "AB");
        Invoice inv = makeInvoice(c, 200.00);
        assertEquals(200.00, inv.subtotal, DELTA);
        assertEquals(10.00, inv.tax, DELTA);
        assertEquals(20.00, inv.discount, DELTA);
        assertEquals(190.00, inv.total, DELTA);
    }
}
