---
name: "Plan request (spec-anchored)"
about: "Step 1 of a change: ask for a reviewed PLAN before any code is written."
title: "[PLAN] "
labels: ["run-3-anchored", "plan"]
---

<!-- Paste the change request / feature ask verbatim below. -->

## The request

_(paste the task here)_

---

## What to deliver in THIS issue's PR

Produce a single new file `docs/plans/PLAN-<this-issue-number>.md` and open a PR
for it. **No code changes in a plan PR** — only the plan document. The plan must
contain, in this order:

1. **Goal** — one paragraph, in terms of observable behavior.
2. **Spec sections relied on** — quote the relevant lines of
   `docs/SPEC-billing.md` you are building on. If the spec is silent or
   ambiguous on something the request needs, say so here as an explicit
   question rather than assuming.
3. **Exact files to touch** — an exhaustive list. Anything not on this list will
   not be changed. Name each file and what changes in it.
4. **Behavior that must NOT change** — cite the specific characterization tests
   in `src/test/...` that lock the behavior you are committing to preserve
   (e.g. loyalty 7%, LEGACY bypass, tax-before-discount, the February due date,
   the one-cent print divergence). These tests stay green and stay unedited.
5. **Tests to add** — the new tests you will write for the new behavior, by name
   and by what each asserts.
6. **Out of scope** — what you are explicitly NOT doing (e.g. "not fixing the
   7% loyalty discrepancy," "not touching due-date logic," "not consolidating
   DiscountUtil"). Pre-existing bugs are out of scope unless the request names
   them.
7. **Risks** — what could go wrong, and how the characterization tests + the new
   tests would catch it.

Do not write implementation code here. The plan is reviewed and merged first;
implementation happens in a separate follow-up issue that references the merged
plan.
