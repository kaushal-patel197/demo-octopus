package com.meridian.invoiceworks;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

/**
 * CHARACTERIZATION TESTS — these pin the CURRENT behavior of the billing path,
 * warts and all. They are NOT a wish list. Several of them assert behavior that
 * is arguably a bug (the 7% loyalty rate, the LEGACY bypass, tax-before-discount,
 * the February off-by-one, the one-cent print divergence). That is deliberate:
 * they define what the code does TODAY so that any change which alters it shows
 * up as a red test. Do not "fix" a test to make a change pass — see
 * docs/SPEC-billing.md and .github/copilot-instructions.md (Process section).
 *
 * All assertions below pass on the untouched codebase.
 */
public class BillingCharacterizationTest {

    private static final double DELTA = 0.0001;

    private Invoice finalized(Customer c, int y, int m, int d, LineItem... items) {
        InvoiceManager mgr = new InvoiceManager();
        Invoice inv = mgr.createInvoice(c, y, m, d);
        for (LineItem li : items) {
            inv.items.add(li);
        }
        mgr.finalizeInvoice(inv);
        return inv;
    }

    // --- discounts --------------------------------------------------------

    // pins current behavior, including known discrepancy: the code applies 7%
    // for LOYALTY-type customers even though the comment (and DiscountUtil, and
    // the README) say 5%. See SPEC-billing "Live discount rules".
    @Test
    public void loyaltyTypeDiscountIsSevenPercent_notTheDocumentedFive() {
        Invoice inv = finalized(new Customer(1, "L", "LOYALTY", 5, "ON"),
                2024, 1, 20, new LineItem("svc", 10, 200.00)); // subtotal 2000
        assertEquals(140.00, inv.discount, DELTA); // 7% of 2000, NOT 100.00 (5%)
    }

    // pins current behavior: LEGACY customers silently bypass ALL discounts,
    // including the volume discount they would otherwise qualify for.
    @Test
    public void legacyCustomerBypassesAllDiscounts_evenOverVolumeThreshold() {
        Invoice inv = finalized(new Customer(2, "Old", "LEGACY", 16, "ON"),
                2024, 1, 22, new LineItem("lic", 60, 100.00)); // subtotal 6000 (>5000)
        assertEquals(0.00, inv.discount, DELTA);   // no volume, no loyalty
        assertEquals(6780.00, inv.total, DELTA);   // 6000 + 13% tax, no discount
    }

    // pins current behavior: the volume discount on the live path is 2%
    // (DiscountUtil, which is dead, uses 3% — see the dead-code test below).
    @Test
    public void volumeDiscountIsTwoPercent_overFiveThousand() {
        Invoice inv = finalized(new Customer(3, "Reg", "REGULAR", 1, "AB"),
                2024, 1, 10, new LineItem("goods", 1, 6000.00)); // subtotal 6000
        assertEquals(120.00, inv.discount, DELTA); // 2% of 6000
    }

    // --- order of operations ---------------------------------------------

    // pins current behavior: tax is charged on the FULL, pre-discount subtotal;
    // the discount is subtracted afterward and does not reduce the tax. The
    // README's worked example (discount first) is wrong. See SPEC-billing
    // "Order of operations".
    @Test
    public void taxIsComputedBeforeDiscount_onTheFullSubtotal() {
        Invoice inv = finalized(new Customer(4, "L", "LOYALTY", 5, "ON"),
                2024, 1, 5, new LineItem("svc", 1, 1000.00)); // subtotal 1000
        assertEquals(130.00, inv.tax, DELTA);      // 13% of full 1000, not of 930
        assertEquals(70.00, inv.discount, DELTA);  // 7%
        assertEquals(1060.00, inv.total, DELTA);   // 1000 + 130 - 70
        // If tax were charged after discount it would be (1000-70)*1.13 = 1050.90.
        assertNotEquals(1050.90, inv.total, DELTA);
    }

    // --- tax special cases ------------------------------------------------

    // pins current behavior: Quebec's rate is served by the "dead-looking"
    // LegacyTaxTable via TaxHelper.getRate. Deleting that class silently breaks
    // QC billing. See SPEC-billing "Province and tax path".
    @Test
    public void quebecTaxRateComesFromLegacyTaxTable() {
        assertEquals(0.14975, LegacyTaxTable.lookup("QC"), DELTA);
        assertEquals(0.14975, TaxHelper.getRate("QC"), DELTA);
        Invoice inv = finalized(new Customer(5, "QC", "REGULAR", 3, "QC"),
                2024, 3, 10, new LineItem("svc", 10, 100.00)); // subtotal 1000
        assertEquals(149.75, inv.tax, DELTA);      // 14.975% of 1000
        assertEquals(1149.75, inv.total, DELTA);
    }

    // pins current behavior: the unfinished getRateNew path (reached only when
    // useNewTaxCalc=true) returns rates that disagree with the live table, which
    // is why the flag must stay false. See SPEC-billing "Config flags".
    @Test
    public void newTaxCalcPathReturnsAWrongRate_soTheFlagStaysFalse() {
        assertEquals(0.05, TaxHelper.getRate("SK"), DELTA);      // live: GST-only default
        assertEquals(0.10, TaxHelper.getRateNew("SK"), DELTA);   // "new" path: wrong 10%
    }

    // --- money representation / rounding ----------------------------------

    // pins current behavior: the printed invoice sums integer cents per line and
    // can land a penny apart from Invoice.total, which rounds once at the end.
    // Trigger amount: two $10.10 lines in Ontario. See SPEC-billing "Monetary
    // representation and rounding".
    @Test
    public void printedTotalDivergesByOnePennyFromInvoiceTotal() {
        InvoiceManager mgr = new InvoiceManager();
        Invoice inv = mgr.createInvoice(new Customer(6, "Acme", "REGULAR", 8, "ON"),
                2024, 1, 15);
        inv.addItem("Widget", 1, 10.10);
        inv.addItem("Gadget", 1, 10.10);
        mgr.finalizeInvoice(inv);
        assertEquals(22.83, inv.total, DELTA);          // double path, rounded once
        String printed = mgr.printInvoice(inv);
        assertTrue("printed TOTAL should be the cents-summed 22.82",
                printed.contains("22.82"));             // integer-cents path
    }

    // --- due dates --------------------------------------------------------

    // pins current behavior: February invoices get a net-29 due date (one day
    // early); every other month is net-30. ReportGen adds the day back, so
    // "fixing" this here breaks the report. See SPEC-billing (open questions)
    // and LANDMINES. Feb 27 -> stored due Mar 28.
    @Test
    public void februaryDueDateIsOneDayEarly() {
        Invoice feb = finalized(new Customer(7, "A", "REGULAR", 8, "ON"),
                2024, 2, 27, new LineItem("x", 1, 100.00));
        assertEquals(2024, feb.dueYear);
        assertEquals(3, feb.dueMonth);
        assertEquals(28, feb.dueDay); // net-29, not net-30 (which would be Mar 29)

        // control: a January invoice gets a normal net-30 date.
        Invoice jan = finalized(new Customer(7, "A", "REGULAR", 8, "ON"),
                2024, 1, 15, new LineItem("x", 1, 100.00));
        assertEquals(2, jan.dueMonth);
        assertEquals(14, jan.dueDay); // Jan 15 + 30 = Feb 14
    }

    // --- approval flag ----------------------------------------------------

    // pins current behavior: invoices whose total exceeds $10,000 get
    // requiresApproval set. See SPEC-billing / LANDMINES (#4).
    @Test
    public void invoiceOverTenThousandRequiresApproval() {
        Invoice big = finalized(new Customer(8, "Big", "LOYALTY", 9, "ON"),
                2024, 1, 25, new LineItem("rollout", 50, 250.00)); // subtotal 12500
        assertEquals(13000.00, big.total, DELTA); // 12500 + 1625 tax - 1125 discount
        assertTrue(big.requiresApproval);

        Invoice small = finalized(new Customer(8, "Big", "REGULAR", 1, "ON"),
                2024, 1, 25, new LineItem("thing", 1, 1000.00));
        assertFalse(small.requiresApproval);
    }

    // --- ReportGen behavior ----------------------------------------------

    // pins current behavior: ReportGen is the ONLY reader of requiresApproval,
    // and it drops flagged invoices from the A/R summary entirely. See
    // SPEC-billing / LANDMINES (#4).
    @Test
    public void reportGenOmitsInvoicesThatRequireApproval() {
        InvoiceManager mgr = new InvoiceManager();
        Invoice normal = mgr.createInvoice(new Customer(9, "Normal", "REGULAR", 1, "ON"), 2024, 1, 1);
        normal.addItem("a", 1, 100.00);
        Invoice big = mgr.createInvoice(new Customer(10, "Whale", "REGULAR", 1, "ON"), 2024, 1, 2);
        big.addItem("b", 1, 20000.00);
        mgr.finalizeInvoice(normal);
        mgr.finalizeInvoice(big);
        assertTrue(big.requiresApproval);

        List<Invoice> list = new ArrayList<Invoice>();
        list.add(normal);
        list.add(big);
        String summary = new ReportGen().arSummary(list);
        assertTrue(summary.contains("#" + normal.number));  // present
        assertFalse(summary.contains("#" + big.number));    // dropped
        assertTrue(summary.contains("Held for approval"));
    }

    // pins current behavior: ReportGen re-derives totals with a drifted copy of
    // the math (loyalty at the documented 5%, not the billed 7%), so reports do
    // not reconcile with billed totals for loyalty customers. See SPEC-billing
    // "Surprising behavior" and LANDMINES (#10).
    @Test
    public void reportGenLoyaltyTotalDisagreesWithBilledTotal() {
        InvoiceManager mgr = new InvoiceManager();
        Invoice inv = mgr.createInvoice(new Customer(11, "Loyal", "LOYALTY", 5, "ON"), 2024, 1, 20);
        inv.addItem("svc", 10, 200.00); // subtotal 2000
        mgr.finalizeInvoice(inv);
        assertEquals(2120.00, inv.total, DELTA); // billed: 7% loyalty

        List<Invoice> list = new ArrayList<Invoice>();
        list.add(inv);
        String summary = new ReportGen().arSummary(list);
        assertTrue("report uses 5% loyalty -> total 2160.00, not the billed 2120.00",
                summary.contains("2160.00"));
    }

    // --- dead code --------------------------------------------------------

    // pins current behavior: DiscountUtil is NOT on the billing path and its
    // numbers disagree with the live inline logic (5% vs 7% loyalty, 3% vs 2%
    // volume). Consolidating onto it would silently change every total. See
    // SPEC-billing "dead code" and LANDMINES (#9).
    @Test
    public void discountUtilIsDeadCodeAndDisagreesWithLivePath() {
        assertEquals(50.00, DiscountUtil.loyaltyDiscount(1000.00), DELTA);  // 5%
        assertEquals(180.00, DiscountUtil.volumeDiscount(6000.00), DELTA);  // 3%

        // the live path bills 7% loyalty for the same subtotal:
        Invoice inv = finalized(new Customer(12, "L", "LOYALTY", 5, "ON"),
                2024, 1, 1, new LineItem("svc", 1, 1000.00));
        assertEquals(70.00, inv.discount, DELTA); // 7%, disagreeing with DiscountUtil's 50.00
    }
}
