# InvoiceWorks — working notes for AI assistants

Context for anyone (human or agent) touching this codebase. Read it before you
change anything. It tells you what the app is, how to build it, how it's laid
out, and where the sharp edges are. It does **not** tell you the exact billing
rules — those live in the spec (see below) or, where the spec is silent, in the
code itself. When in doubt, read the code; do not assume.

## What this is

Meridian Business Systems — **InvoiceWorks v2.4.1**, a small-business invoicing
app maintained since ~2009. It creates customers and invoices, computes totals
(line items, discounts, tax, rounding), prints invoices as text, runs a
month-end A/R report, and persists to a flat file. No database. No web layer.
Java 8, Maven, JUnit 4. Single package: `com.meridian.invoiceworks`.

## Build & run

- Build: `mvn package`
- Run the sample day: `java -jar target/invoiceworks.jar`
- Tests: `mvn test` (the `package` phase runs them too)

`Main` creates a handful of customers and invoices and prints them plus the A/R
summary — it's the closest thing to a regression check. Eyeball its output
before and after any change.

## Architecture map (5 things to know)

1. **`InvoiceManager` is a God class** — creation, totals, discounts, tax, the
   text printout, and persistence all live here. `finalizeInvoice()` is the
   authoritative money path: the numbers it writes onto an `Invoice` are what
   the business bills.
2. **Models are anemic** — `Customer`, `Invoice`, `LineItem` are public-field
   data holders with essentially no behavior. Don't go looking for logic there.
3. **Tax** resolves through `TaxHelper` (the modern rate table), with one
   province routed through `LegacyTaxTable`. Rate selection also honors a
   config flag — see danger zones.
4. **Discounts exist in two places** — an inline block inside `InvoiceManager`
   and a separate `DiscountUtil`. They do **not** agree, and only one of them
   is actually on the billing path. Know which before you touch either.
5. **`ReportGen`** re-derives totals for reporting with its own copy of the
   math, which has drifted from `InvoiceManager` over the years. It is the only
   reader of some invoice fields. Reports and invoices don't always reconcile.

## Conventions

- **Match the surrounding style.** This is legacy code: public fields, `double`
  for money, static helpers, hand-rolled date math, JUnit 4. Follow what the
  file next to you does — do not modernize opportunistically (no BigDecimal
  sweep, no records, no streams refactor, no reformatting untouched code).
- Keep changes small and local. A one-line behavior change should be a one-line
  diff, not a refactor.
- Java 8 language level only. Don't introduce APIs newer than Java 8.

## Danger zones (read before editing)

- **`LegacyTaxTable` looks dead but is not.** It is reached on a live code path.
  Do not delete it or inline it away without tracing every caller first.
- **Config flags in `config.properties` change behavior.** `useNewTaxCalc` must
  stay `false` — the "new" tax path was never finished and returns wrong
  numbers. Other flags alter printing and reporting. Don't flip flags to "clean
  up" and don't hardcode around them.
- **Money representation is inconsistent.** Most paths use `double` dollars; at
  least one path sums in integer cents. The same invoice added up two ways can
  differ by a penny. Know which representation you're in before you change a
  total, and don't "unify" them without understanding why they differ.
- **Totals are subtle.** The order of operations for subtotal, discount, and
  tax is not necessarily the one you'd guess, and the discount rules depend on
  customer type. Confirm current behavior (spec or code) before changing it;
  what a comment or the README says may not be what the code does.
- **Don't fix bugs you weren't asked to fix.** Some odd-looking behavior is
  depended on elsewhere. If you spot something that looks wrong, note it — don't
  silently "correct" it as part of an unrelated change.

## The spec

`docs/SPEC-billing.md` is the recovered, behavior-accurate description of how
invoice totals are actually computed — treat it as the source of truth for
billing behavior and cite it when you rely on it. (If that file isn't present
yet, it hasn't been recovered; fall back to reading the code.)
