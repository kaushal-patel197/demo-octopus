# Invoice billing behavior spec (recovered from code)

## Scope and authoritative path

- The billed amount is the `Invoice.total` written by `InvoiceManager.finalizeInvoice()`; comments call this the authoritative money path. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:16-18`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:53-85`)
- `Invoice` money fields are explicitly described as populated by `finalizeInvoice()` and not trustworthy before that. (`src/main/java/com/meridian/invoiceworks/Invoice.java:27-31`)

## Order of operations (line items, discounts, tax, rounding)

`finalizeInvoice()` computes totals in this order:

1. **Subtotal** = sum of all `LineItem.lineTotal()` values. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:63-66`, `src/main/java/com/meridian/invoiceworks/LineItem.java:22-25`)
2. **Tax** = `subtotal * rateFor(province)` (tax is calculated on full pre-discount subtotal). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:68-70`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:120-123`)
3. **Discount** = `computeDiscount(customer, subtotal)` (discount is based on subtotal, not tax-adjusted amount). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:71-73`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:94-116`)
4. **Total** = `subtotal + tax - discount`. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:74`)
5. **Rounding**: `subtotal`, `tax`, `discount`, and `total` are each rounded to cents with `Math.round(v * 100.0) / 100.0`. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:76-79`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:180-182`)

## Live discount rules (billing path)

`computeDiscount()` in `InvoiceManager` is the live discount logic:

- `LEGACY` customer type: always discount `0.0` (short-circuits all other discount logic). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:95-100`)
- Long-standing customer discount: if `customer.yearsWithUs > 2`, adds **10%** of subtotal (applies to all non-LEGACY customers). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:105-107`)
- `LOYALTY` customer type: adds **7%** of subtotal. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:110-112`)
- Volume discount: if `subtotal > 5000.0`, adds **2%** of subtotal. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:109-113`)
- These rules stack for non-LEGACY customers: a long-standing (>2 years) LOYALTY customer over the volume threshold gets 10% + 7% + 2%. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:102-117`)

## Discount rules that are dead code (or dead for billing)

- `DiscountUtil` is explicitly documented as an abandoned extraction that is not used by `InvoiceManager` for billed totals. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:90-93`, `src/main/java/com/meridian/invoiceworks/DiscountUtil.java:6-11`)
- `DiscountUtil` defines different rules than billing path:
  - loyalty = 5% (`loyaltyDiscount`) (`src/main/java/com/meridian/invoiceworks/DiscountUtil.java:15-18`)
  - volume = 3% when subtotal > 5000 (`volumeDiscount`) (`src/main/java/com/meridian/invoiceworks/DiscountUtil.java:21-25`)
  - combined in `totalDiscount()` (`src/main/java/com/meridian/invoiceworks/DiscountUtil.java:29-35`)
- These `DiscountUtil` rules are therefore **dead for invoice billing**.
- `ReportGen.reportTotal()` also has its own discount math (LOYALTY 5%, volume 2%, LEGACY no discount), but this is for reporting output, not billed invoice totals. (`src/main/java/com/meridian/invoiceworks/ReportGen.java:53-78`)

## Special customer types and config flags that change behavior

### Customer types

- Supported customer type values are documented as `REGULAR`, `LOYALTY`, `LEGACY`. (`src/main/java/com/meridian/invoiceworks/Customer.java:6-8`, `src/main/java/com/meridian/invoiceworks/Customer.java:14-15`)
- `LOYALTY` and `LEGACY` alter discount behavior as described above. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:95-113`)

### Province and tax path

- Tax rate selection uses province code and `TaxHelper.getRate(...)` by default. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:129-137`, `src/main/java/com/meridian/invoiceworks/TaxHelper.java:29-46`)
- `QC` is special-cased to use `LegacyTaxTable.lookup("QC")`, returning `0.14975`. (`src/main/java/com/meridian/invoiceworks/TaxHelper.java:41-44`, `src/main/java/com/meridian/invoiceworks/LegacyTaxTable.java:24-27`)

### Config flags

- `useNewTaxCalc` controls whether `rateFor()` uses `TaxHelper.getRateNew(...)` (experimental path) vs `TaxHelper.getRate(...)` (default/live path). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:126-137`)
- Shipped config sets `useNewTaxCalc=false`. (`src/main/resources/config.properties:4-7`)
- `TaxHelper.getRateNew(...)` is documented as unfinished/wrong and uses legacy lookup with a 10% fallback for missing provinces. (`src/main/java/com/meridian/invoiceworks/TaxHelper.java:49-62`)
- `legacyPrintFooter` changes invoice printout footer only, not billing math. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:230-232`, `src/main/resources/config.properties:8-9`)

## Monetary representation and rounding behavior

- Core billing uses `double` dollars (`LineItem.unitPrice`, invoice money fields, subtotal/tax/discount/total computation). (`src/main/java/com/meridian/invoiceworks/LineItem.java:11`, `src/main/java/com/meridian/invoiceworks/Invoice.java:27-31`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:63-79`)
- Main billing path rounds at the end of each computed field via `round2(...)`. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:76-79`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:180-182`)
- Printed invoice totals use a separate cents-based accumulation path:
  - line subtotal cents: `Math.round(li.lineTotal() * 100.0)`
  - line tax cents: `Math.round(li.lineTotal() * rate * 100.0)`
  - total cents: `subtotalCents + taxCents - discountCents` (`discountCents` from rounded `inv.discount`) (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:196-225`)
- Because of per-line cent rounding in print path vs aggregate rounding in billing path, printed `TOTAL` may differ from `Invoice.total` by 1 cent. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:187-191`)

## Surprising/important behavior

- Tax is computed before discount and discount does not reduce taxable base (contrary to README example). (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:57-60`, `README.md:8-10`)
- Live loyalty discount is 7%, while README and reporting path use 5%. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:104-107`, `README.md:12`, `src/main/java/com/meridian/invoiceworks/ReportGen.java:55-56`, `src/main/java/com/meridian/invoiceworks/ReportGen.java:68-70`)
- `ReportGen` recomputes totals and may disagree with billed totals (notably for loyalty customers). (`src/main/java/com/meridian/invoiceworks/ReportGen.java:8-13`, `src/main/java/com/meridian/invoiceworks/ReportGen.java:53-78`)

## Open questions / ambiguities

1. `rollReportsOnFirst` exists in config but no read site was found in main Java sources during code inspection; unclear whether this flag is currently inert or used by external/runtime tooling. (`src/main/resources/config.properties:11-13`)
2. Customer type handling is string-equality based for specific literals (`"LEGACY"`, `"LOYALTY"`); behavior for other/non-standard values is implicit (treated as non-LOYALTY and non-LEGACY), but not formally validated anywhere. (`src/main/java/com/meridian/invoiceworks/InvoiceManager.java:98`, `src/main/java/com/meridian/invoiceworks/InvoiceManager.java:105`, `src/main/java/com/meridian/invoiceworks/Customer.java:6-8`)
