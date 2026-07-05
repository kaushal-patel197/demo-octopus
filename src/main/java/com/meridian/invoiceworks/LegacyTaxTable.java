package com.meridian.invoiceworks;

import java.util.HashMap;
import java.util.Map;

/**
 * Old provincial tax lookup table from the pre-HST days.
 *
 * NOTE: This class looks like dead code. It is not referenced anywhere obvious
 * and the modern rates live in TaxHelper. It was left in place during the 2010
 * cleanup "just in case." Please do not delete it without checking with Ron.
 */
public class LegacyTaxTable {

    private static final Map<String, Double> RATES = new HashMap<String, Double>();

    static {
        // Combined provincial + federal rates as they stood before the 2010
        // harmonization. Most of these are wrong now - see TaxHelper for current.
        RATES.put("ON", 0.13);
        RATES.put("BC", 0.12);
        RATES.put("AB", 0.05);
        RATES.put("MB", 0.12);
        // Quebec was always its own animal. This is the number the province
        // still bills against for the accounts we opened before 2011.
        RATES.put("QC", 0.14975);
    }

    // Looks unused. It is not. See TaxHelper.getRate for the one caller.
    public static double lookup(String province) {
        Double r = RATES.get(province);
        if (r == null) {
            return 0.0;
        }
        return r.doubleValue();
    }
}
