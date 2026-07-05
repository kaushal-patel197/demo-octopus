# PLAN-8 — Add loyalty discount for long-standing customers

1. **Goal**

When an invoice is finalized, customers who have been with us for more than 2 years should receive a 10% loyalty discount in the billed totals (`Invoice.discount` and `Invoice.total`) while preserving the current billing flow: tax is still computed on full pre-discount subtotal, LEGACY customers still bypass discounts, and existing volume discount behavior remains intact unless explicitly changed by clarified requirements.

2. **Spec sections relied on**

From `docs/SPEC-billing.md`:

- "**Tax** = `subtotal * rateFor(province)` (tax is calculated on full pre-discount subtotal)."
- "**Discount** = `computeDiscount(customer, subtotal)` (discount is based on subtotal, not tax-adjusted amount)."
- "**Total** = `subtotal + tax - discount`."
- "`LEGACY` customer type: always discount `0.0` (short-circuits all other discount logic)."
- "Volume discount: if `subtotal > 5000.0`, adds **2%** of subtotal."
- "These rules stack for non-LEGACY loyalty customers over the threshold (7% + 2%)." (SPEC section: **Live discount rules (billing path)**)

Spec ambiguity/questions to resolve before implementation:

- The current spec keys loyalty discount off `customerType == "LOYALTY"`, while the request keys it off tenure (`yearsWithUs > 2`). Should 10% apply to **all** non-LEGACY customers with `yearsWithUs > 2`, or only customers whose `customerType` is `LOYALTY` **and** tenure is >2?
- If tenure-based 10% applies, does it **replace** the existing 7% loyalty component or exist as an additional stacking component? (Request says "existing discounts" should continue to work.)

Implementation gate for these ambiguities:

- Before implementation starts, obtain explicit written resolution from the issue requester/product owner on both questions above in issue #8 (or linked plan review comments), then implement exactly that resolution.
- Until that resolution is recorded, no billing-code implementation should proceed from this plan.

3. **Exact files to touch**

- `/home/runner/work/demo-octopus/demo-octopus/src/main/java/com/meridian/invoiceworks/InvoiceManager.java`
  - Update `computeDiscount(...)` to implement the approved 10% long-standing-customer loyalty rule, while preserving LEGACY bypass, volume threshold logic, and existing tax/total order of operations.
- `/home/runner/work/demo-octopus/demo-octopus/src/test/java/com/meridian/invoiceworks/LoyaltyTenureDiscountTest.java` (new)
  - Add focused tests for the new loyalty rule and interactions with existing discount/tax behavior.
- `/home/runner/work/demo-octopus/demo-octopus/docs/SPEC-billing.md`
  - Update "Live discount rules" / related sections so the spec reflects the merged behavior change.

4. **Behavior that must NOT change**

The following characterization tests in `/home/runner/work/demo-octopus/demo-octopus/src/test/java/com/meridian/invoiceworks/BillingCharacterizationTest.java` must remain green and unedited:

- `legacyCustomerBypassesAllDiscounts_evenOverVolumeThreshold` (LEGACY discount bypass)
- `taxIsComputedBeforeDiscount_onTheFullSubtotal` (tax-before-discount order)
- `quebecTaxRateComesFromLegacyTaxTable` (QC/legacy tax path)
- `februaryDueDateIsOneDayEarly` (due-date quirk)
- `printedTotalDivergesByOnePennyFromInvoiceTotal` (one-cent print divergence)
- `discountUtilIsDeadCodeAndDisagreesWithLivePath` (DiscountUtil remains off billing path)

Note: `loyaltyTypeDiscountIsSevenPercent_notTheDocumentedFive` is expected to change only in the follow-up implementation PR (not this plan PR) after this plan is approved and ambiguity resolution is recorded; in that implementation PR, it should be updated/replaced with tests that pin the approved post-resolution loyalty behavior (not loosened).

5. **Tests to add**

In `/home/runner/work/demo-octopus/demo-octopus/src/test/java/com/meridian/invoiceworks/LoyaltyTenureDiscountTest.java`:

- `longStandingCustomerGetsTenPercentLoyaltyDiscount()`
  - Asserts a >2-year eligible customer receives 10% discount in finalized invoice.
- `exactlyTwoYearsDoesNotGetLongStandingLoyaltyDiscount()`
  - Asserts boundary condition: 2 years is not eligible for the >2-years rule.
- `longStandingDiscountStacksWithVolumeDiscountForEligibleCustomer()`
  - Asserts stacking with existing 2% volume discount (for non-LEGACY eligible customer over threshold), unless clarified otherwise.
- `legacyCustomerStillBypassesDiscountsEvenIfLongStanding()`
  - Asserts LEGACY short-circuit remains 0 discount even when tenure >2.
- `taxComputationOrderUnchangedWithLongStandingDiscount()`
  - Asserts tax is still calculated on full subtotal before discount when new loyalty rule applies.

6. **Out of scope**

- Not refactoring or consolidating discount logic into `DiscountUtil`.
- Not changing due-date computation or approval-threshold behavior.
- Not changing tax table selection/config flag behavior (`useNewTaxCalc`).
- Not addressing unrelated report-path drift in `ReportGen` unless explicitly requested.
- Not broad modernization changes (money type, architecture, formatting-only sweeps).

7. **Risks**

- **Risk: wrong eligibility interpretation** (customer type vs tenure basis) could discount the wrong customers. The boundary and eligibility tests above catch this.
- **Risk: accidental change to discount stacking** could under/over-discount high-value invoices. The stacking test catches this.
- **Risk: accidental change to order of operations** could alter tax amounts. The new tax-order test plus existing characterization test catches this.
- **Risk: regressions to protected legacy behavior** (LEGACY bypass, due dates, print rounding divergence, dead DiscountUtil path). Existing characterization tests listed above catch these regressions.
