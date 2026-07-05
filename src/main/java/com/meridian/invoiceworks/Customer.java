package com.meridian.invoiceworks;

/**
 * A customer record. Anemic on purpose - all the logic lives in InvoiceManager.
 *
 * customerType is one of: "REGULAR", "LOYALTY", "LEGACY".
 * We stopped creating LEGACY accounts around 2012 but a handful are still active.
 */
public class Customer {

    public int id;
    public String name;

    // "REGULAR", "LOYALTY", "LEGACY"
    public String customerType;

    // How long they have been a customer, in whole years.
    public int yearsWithUs;

    // Two-letter province code: ON, QC, BC, AB, MB, ...
    public String province;

    public Customer() {
        this.customerType = "REGULAR";
        this.province = "ON";
    }

    public Customer(int id, String name, String customerType, int yearsWithUs, String province) {
        this.id = id;
        this.name = name;
        this.customerType = customerType;
        this.yearsWithUs = yearsWithUs;
        this.province = province;
    }

    public String toString() {
        return "Customer#" + id + " " + name + " (" + customerType + ", " + province + ")";
    }
}
