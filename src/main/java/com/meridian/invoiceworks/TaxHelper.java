package com.meridian.invoiceworks;

/**
 * Tax rate helper. Static methods, hardcoded rates. This is the modern path.
 *
 * Rates are combined sales tax (GST/HST or GST+PST) by province. They were
 * last reviewed in 2013 so double-check before quoting them to a customer.
 */
public class TaxHelper {

    // Ontario HST
    private static final double ON_RATE = 0.13;
    // British Columbia (GST + PST, de-harmonized in 2013)
    private static final double BC_RATE = 0.12;
    // Alberta - GST only
    private static final double AB_RATE = 0.05;
    // Manitoba
    private static final double MB_RATE = 0.12;
    // Everyone else we treat as GST-only until accounting says otherwise.
    private static final double DEFAULT_RATE = 0.05;

    /**
     * Current combined tax rate for a province code.
     *
     * Quebec is special-cased. The rate the province bills our older accounts
     * against never made it into the modern table, so we still read it out of
     * the old lookup. Do not "simplify" this by hardcoding a QC rate here.
     */
    public static double getRate(String province) {
        if (province == null) {
            return DEFAULT_RATE;
        }
        if (province.equals("ON")) {
            return ON_RATE;
        } else if (province.equals("BC")) {
            return BC_RATE;
        } else if (province.equals("AB")) {
            return AB_RATE;
        } else if (province.equals("MB")) {
            return MB_RATE;
        } else if (province.equals("QC")) {
            // The one live call into the "dead" table.
            return LegacyTaxTable.lookup("QC");
        }
        return DEFAULT_RATE;
    }

    /**
     * Experimental rate lookup written for the 2011 HST rollout. It was never
     * finished and returns rates that are simply wrong for most provinces.
     * Only reached when config useNewTaxCalc=true, which must stay false.
     */
    public static double getRateNew(String province) {
        // BUG: this reads the pre-harmonization numbers and treats them as
        // current, and it forgets Quebec entirely. Left here as a warning.
        double r = LegacyTaxTable.lookup(province);
        if (r == 0.0) {
            // "close enough" - it is not
            return 0.10;
        }
        return r;
    }
}
