---
name: "Implement plan (spec-anchored)"
about: "Step 2 of a change: implement an already-merged plan, exactly."
title: "[IMPL] "
labels: ["run-3-anchored"]
---

## Plan to implement

Implement `docs/plans/PLAN-<n>.md` exactly. _(Replace `<n>` with the merged
plan's number and link it here.)_

## Rules

- **Follow the plan.** Change only the files the plan lists. Add only the tests
  the plan names.
- **If reality contradicts the plan — stop and comment on this issue.** Do not
  improvise, do not expand scope, do not "while I'm here" anything. A plan that
  turns out to be wrong is fixed by updating the plan, not by freelancing in the
  implementation.
- **All existing characterization tests stay green and stay unedited.** They pin
  current behavior on purpose (including known bugs). If your change legitimately
  needs to alter one, that must already be called out in the merged plan with a
  reason; otherwise a red characterization test means your change broke something
  it shouldn't have.
- **Add the new tests named in the plan**, and make them pass.
- The diff should match the plan's file list. If it doesn't, say why on the
  issue before requesting review.
