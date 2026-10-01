# Decision: the Ministry's four rulings on Schedule 5 (Camp & Access Expenses)

**Status:** **ANSWERED 2026-08-27** by the Ministry (@paulushcgcj), on the four questions raised in #370. All four rulings are recorded below with the answer quoted verbatim.
**Scope:** Schedule 5 (Camp & Access Expenses), Epic 7. Questions raised during the #242 and #258 reviews, batched alongside the Schedule 10 questions on #193.

This is the authoritative in-repo record of what was decided and why. #370 asked the questions and was closed once answered; the answers live here so the code that depends on them can cite something durable.

**No ruling reverted shipped behaviour.** Three rulings confirmed what was already built; the fourth declined a tightening we had offered, which also left the shipped code correct. Nothing had to be unwound.

## The four rulings at a glance

| # | Question | Ruling | Effect on shipped code |
|---|---|---|---|
| **1** | Road distance: is the maximum `999999.9` or `999,999`? | `999999.9` is correct — **the message was the defect** | Bound unchanged; message text corrected |
| **2** | Wages and Benefits accepts 10× the other categories — on purpose? | **Intentional.** Our proposed tightening declined | None — shipped bound already correct |
| **3** | Recoveries can't be negative where others can — on purpose? | **Correct as built.** Zero floor is right | None |
| **4** | A valid mill and year with no camps — error, or empty page? | **Empty page**, plus a "no camps" message | Contract unchanged; empty-state message added |

Two enhancements fell out of the answers rather than out of any defect — see [Follow-ups](#follow-ups) below. Both are delivered in #403.

---

## 1. Road distance: the rule allowed more than the message said

**Question:** is `999999.9` the intended maximum with the on-screen message simply imprecise, or was `999,999` intended, making the stored `999999.9` rows bad data?

**Ruling — @paulushcgcj, 2026-08-27:**

> Subgrade is in KM, so the correct is the one with the dot, like `999999.9`. The suggested fix is the best, and if is possible to add a KM at the front, just to be more intuitive would be even better.

**`999999.9` is the intended maximum. The error message text is the defect.**

The validator bound was confirmed and **did not move** — `CampRequest.roadDistanceToOperatingArea` keeps `@DecimalMax("999999.9")`, and the camps stored at exactly `999999.9` are good data, not corruption.

**The rationale that was missing from the source: the field is in kilometres.** That is why it carries a decimal at all, and it appears nowhere in the legacy code. It is recorded here because it is the reason the bound is what it is.

What changed: `distanceValidatorErrorMsg` now reads `Entered distance must be between 0 and 999,999.9.` Note this makes the message a **deliberate, Ministry-sanctioned departure from the legacy text** — the bundle line carries a comment saying so, so that a later legacy-fidelity pass does not "restore" the wrong string. The key is shared with Schedule 4, which enforces the identical band, so both schedules move together.

## 2. Wages and Benefits accepts ten times more than every other category

**Question:** is the wider ±99,999,999 range intentional, or a missing `costSize="7"` that should be tightened to match the other eight?

**Ruling — @paulushcgcj, 2026-08-27:**

> Keep it as is, as this is somewhat on purpose.

**The wider ±99,999,999 bound on Wages and Benefits is intentional. No change.**

Our recommendation — tighten it to match the siblings — was **declined**, which is the zero-cost outcome: the shipped code already enforced the wider bound. `CategoryEntry.cost` keeps `@Min/@Max ±99,999,999`, and `Schedule5Service.validateCostRanges()` correctly continues to omit `wagesAndBenefits` from the standard-range check.

⚠️ **This bound is now ratified, not merely inherited.** The standing risk is that someone reads the one input missing its `costSize` attribute as a legacy bug and "fixes" it. That would contradict this ruling. Both `CategoryEntry` and `validateCostRanges()` carry a comment saying so.

## 3. Recoveries can't be negative where every other category can

**Question:** is the zero floor on Recoveries intended for Schedule 5, or inherited from Schedule 9 by copy?

**Ruling — @paulushcgcj, 2026-08-27** (two comments):

> It should be a positive value, so the zero floor is the correct approach.

> In theory, none of the entries in there should be negative. But it is correct for recoveries; it should be zero or positive numbers for recoveries.

**The zero floor on Recoveries is correct for Schedule 5. No change.** `Schedule5Service.validateCostRanges()` keeps Recoveries at 0–9,999,999. That the message key is named for Schedule 9 does not affect the ruling — the bound is right for Schedule 5 on its own merits.

⚠️ **One part of this answer was deliberately not acted on, and we want that visible rather than buried.** The opening sentence of the second comment — *"none of the entries in there should be negative"* — is broader than the question asked. Read literally it would put a zero floor on the other eight categories, which today accept ±9,999,999 exactly as the current application does. We did **not** apply it, because the same comment immediately scopes the ruling back to Recoveries (*"But it is correct for recoveries"*), and changing eight fields' validation is not something to infer from an aside.

**If the broader reading was intended, please say so** — it is a real change request against shipped behaviour, and we will size and schedule it properly rather than slipping it in.

## 4. A valid mill and year with no camps yet

**Question:** confirm that a valid mill and year with no camps opens an empty Schedule 5 rather than showing an error.

**Ruling — @paulushcgcj, 2026-08-27:**

> Display a blank page. If possible, have a message such as no camps or something similar

**Confirmed — a blank page, not an error.**

The shipped read stands unchanged: a missing mill/year reporting context returns `404`, while a valid context with no camps returns `200` with an empty camp list. The requirement wording that read ambiguously ("no Schedule 5 record → error") is settled as meaning the missing-context case.

The "no camps" message was granted as an addition. It is **presentation only — the `200` empty-list API contract does not change**, so nothing in the backend or its tests is affected.

**Scope note:** the empty-state message was asked for on Schedule 5. Schedule 10 shares the same `200`-empty boundary by design, and that boundary is now confirmed — but the *message* was not requested there, so it has not been generalised.

---

## Follow-ups

Neither is a correction of shipped behaviour; both are additions the Ministry asked for while answering.

| Follow-up | From | Status |
|---|---|---|
| **FU-A** — correct `distanceValidatorErrorMsg` to "0 and 999,999.9"; surface KM on the road distance field | Ruling 1 | **Delivered** in #403. The KM unit was already present — the field has read "Road Distance to Operating Area (km)" since `dc6c1bb`, before the question was asked |
| **FU-B** — show a "no camps" empty state instead of a bare empty grid | Ruling 4 | **Delivered** in #403, as `No camps have been added.` |

## Where these rulings are relied on in code

Each of these carries a comment citing this decision, so the reasoning survives contact with a future reader:

- `backend/src/main/resources/messages.properties` — `distanceValidatorErrorMsg`, the sanctioned departure from legacy text (ruling 1)
- `backend/src/main/java/ca/bc/gov/nrs/ilcr/schedule5/dto/CampRequest.java` — the `999999.9` bound and its kilometres rationale (ruling 1)
- `backend/src/main/java/ca/bc/gov/nrs/ilcr/schedule5/dto/CategoryEntry.java` — the ratified ±99,999,999 cost bound (ruling 2)
- `backend/src/main/java/ca/bc/gov/nrs/ilcr/schedule5/Schedule5Service.java` — `validateCostRanges()` omitting `wagesAndBenefits` (ruling 2), the Recoveries zero floor (ruling 3), and `getSchedule5()` serving `200`-empty (ruling 4)
- `backend/src/test/java/ca/bc/gov/nrs/ilcr/schedule5/Schedule5WriteValidationIT.java` — the acceptance notes for all of the above

## Provenance

- **Questions asked:** #370 (closed once answered; the question text and the inline answers remain there)
- **Raised during:** #242 and #258
- **Batched with:** the Schedule 10 questions on #193
- **Tracking issue:** #264
