# Four questions for the Ministry on Schedule 5 (Camp & Access Expenses)

**Status:** Open — waiting on your input. Raised 2026-08-27.
**Scope:** Schedule 5 (Camp & Access Expenses). The backend went in with #258; the page and sub-pages followed.

Hello! While rebuilding Schedule 5 we ran into four small places where the existing application seems to disagree with itself, and we'd like your read on what was actually intended.

**None of this is holding us up.** All four behaviours are already built and working, matching what the current application does today. We've made a recommendation on each one, so in most cases confirming it is a one-line reply. If you'd rather we changed something, that's completely fine — just say so and we'll take care of it.

We've batched these alongside the Schedule 10 questions on #193 so they're easy to work through together.

## How to answer

Comment inline on whichever question you're answering, or leave one comment at the top of this PR covering all four — whatever's easiest. Each question below ends with **the one line that closes it**, so if our recommendation looks right, quoting that line back is all we need. **The last one needs a single word.**

Only question 2 involves meaningful rework if you'd like it changed, and we've sized that below so you can weigh it. The other three are quick either way.

## Why we couldn't just read the code

Our approach throughout the rebuild has been to match the existing application exactly — where there's a choice to make, we do what the current system does rather than quietly improving it, so nothing changes underneath licensees without the Ministry asking for it.

These four are the cases where **the existing application is inconsistent with itself**: a validation rule that disagrees with the error message it shows, one field out of ten missing a setting all its neighbours have, an error message borrowed from a different schedule. The code tells us clearly *what* happens today, but not *what was meant* — and the two readings point at genuinely different products. That's a business question, not a technical one, which is why we're bringing it to you.

## The four questions at a glance

| # | Question | What it does today | What we'd suggest | If you'd rather change it |
|---|---|---|---|---|
| **1** | Road distance: is the maximum `999999.9` or `999,999`? | Accepts `0.0`–`999999.9` | **Keep `999999.9`** — the error message wording is what's wrong | Costly — real camps are stored at exactly `999999.9` and would become un-editable |
| **2** | Wages and Benefits accepts 10× more than the other categories — on purpose? | Accepts ±99,999,999 | **Tighten it to ±9,999,999** to match the other eight | About half a day, across the backend, the page and the tests |
| **3** | Recoveries can't be negative where the others can — on purpose? | Accepts 0–9,999,999 | **Keep the zero floor** — a recovery is a positive amount that gets subtracted | Small — one limit and one message |
| **4** | A valid mill and year with no camps yet — error, or empty page? | Opens an empty Schedule 5 | **Confirm the empty page** | Small, but it would work differently from the current application |

---

## 1. Road distance: the rule allows more than the message says

**Today:** Road Distance to Operating Area accepts `0.0` through `999999.9`.

### What the existing code shows

The validation rule caps the value at **999999.9**:

```java
// ILCRDistanceValidator.java:16-17
private static final BigDecimal UPPER = new BigDecimal("999999.9");
private static final BigDecimal LOWER = new BigDecimal("0.0");
```

But the message a licensee sees when they go over it reads:

```properties
# messages.properties:122
distanceValidatorErrorMsg=Entered distance must be between 0 and 999,999.
```

The message drops the `.9`. One of the two is wrong, and they point at different answers: either the field really does allow `999999.9` and the message is just imprecise, or `999,999` was meant all along and the rule is 0.9 too generous.

### Why we couldn't settle it ourselves

Both readings hold together on their own. What tips it is the data: **there are camps in the live system stored with a road distance of exactly `999999.9`.** If we tightened the field to match the message, those camps would fail validation the moment someone opened and re-saved them — the record would become un-editable without anyone having changed a thing.

So we kept the rule as-is and reproduced the message word for word, imprecision included.

### The question

**Is `999999.9` the intended maximum — with the on-screen message simply imprecise — or was `999,999` intended, which would mean those stored rows are bad data?**

### What we'd suggest

**`999999.9` is the maximum, and the message wording is what needs fixing.** The stored data is the best evidence we have of what was intended, and correcting a message costs nothing where correcting the limit would strand existing records.

If you agree, we'd also like to update the message to read `between 0 and 999,999.9` so the screen stops contradicting itself — but do let us know if you'd prefer we leave the wording exactly as licensees see it today. Happy either way.

> **One line closes this:** *"999999.9 is the maximum — please update the message to match."*

---

## 2. Wages and Benefits accepts ten times more than every other category

**Today:** the Wages and Benefits Cost $ field accepts **±99,999,999**. Its eight ordinary neighbours accept ±9,999,999.

### What the existing code shows

The camp grid has ten editable Cost $ fields. The shared validation rule picks its limit from a `costSize` setting on each field:

```java
// ILCRCostValidator.java:29 — no setting means "8", which matches none of the cases below
String costSize = component.getAttributes().get("costSize") == null ? "8" : (String)component.getAttributes().get("costSize");
...
} else if (costSize.equals("7")) {                       // :40-43
    costMinSize = COST_7_MIN_VALUE;  // -9,999,999
    costMaxSize = COST_7_MAX_VALUE;  //  9,999,999
    errMsgProperty = "costSize7ValidatorErrorMsg";
} else {                                                 // :48-51 — the fall-through
    costMinSize = COST_MIN_VALUE;    // -99,999,999
    costMaxSize = COST_MAX_VALUE;    //  99,999,999
    errMsgProperty = "costValidatorErrorMsg";
}
```

Of the ten fields:

| Field | `costSize` | Limit enforced |
|---|---|---|
| Catering and Food, Depreciation/Lease, General Camp Expenses, Other Camp Expenses, Crew Transportation, Equipment & Supplies (Land / Rail / Air / Water) | `"7"` | ±9,999,999 |
| Recoveries | `"0"` | 0–9,999,999 *(see question 3)* |
| **Wages and Benefits** | **none** | **±99,999,999** |

`schedule5ExistingCamp.xhtml:160-162` closes the Wages and Benefits field without a `<f:attribute name="costSize" .../>` line, where all its neighbours have one (`:140`, `:183`, `:206`, `:252`, `:293`, `:325`, `:347`). The add-a-camp form does the same (`schedule5NewCamp.xhtml:99-101`). A side effect is that licensees see a different error message on this one field — *"between -99,999,999 and 99,999,999"* rather than *"between -9,999,999 and 9,999,999"*.

### Why we couldn't settle it ourselves

**Both forms leave it out, so this is consistent rather than a one-off slip** — which is exactly what makes it hard to call. A single missing setting on one field out of ten looks like something that was overlooked and then copied across when the second form was written. But Wages and Benefits is also plausibly the one camp category that genuinely runs an order of magnitude larger than the rest, in which case the wider limit is deliberate and the missing setting is simply how it was expressed. The code doesn't tell us which.

We kept the wider limit exactly as it is rather than tidying it up.

### The question

**Is the wider ±99,999,999 limit on Wages and Benefits intentional, or a missing `costSize="7"` that should be tightened to match the other eight?**

### What we'd suggest

**Our hunch is that it was overlooked, and it should be tightened to ±9,999,999.** Nine of the ten categories agree; the tenth differs by something being *absent* rather than by anything positively saying "this one is different"; and no camp in the live data comes anywhere near needing the wider range.

That said, this is really a question about what a camp's wage line can legitimately reach — your call, not ours, and we'd rather ask than assume.

### What changes if you'd like it tightened

This is the one question of the four where your answer means touching working code. It's small but real:

- the limit in the backend,
- the matching limit on the Schedule 5 page,
- the tests that assert it.

Roughly half a day including tests. **Confirming it as-is costs nothing** — the code already behaves that way.

> **One line closes this:** *"Wages and Benefits should match the others — please tighten it to ±9,999,999"* — or — *"the wider range is intentional, please leave it."*

---

## 3. Recoveries can't be negative, where every other category can

**Today:** the Recoveries Cost $ field accepts **0–9,999,999**. It's the only camp category with a floor of zero.

### What the existing code shows

Recoveries alone carries `costSize="0"` (`schedule5ExistingCamp.xhtml:252`, `schedule5NewCamp.xhtml:150`), which selects:

```java
// ILCRCostValidator.java:32-35
if (costSize.equals("0")) {
    costMinSize = COST_ZERO_MIN_VALUE;   // 0
    costMaxSize = COST_7_MAX_VALUE;      // 9,999,999
    errMsgProperty = "costValidatorSchedule9ErrorMsg";
}
```

Worth noting the message name: **`costValidatorSchedule9ErrorMsg`** — a Schedule *9* message, used on a Schedule *5* field. Its text is `Entered cost must be between 0 and 9,999,999.` (`messages.properties:74`).

### Why we couldn't settle it ourselves

There's a perfectly sound business reason for the floor. Recoveries is left out of the Camp Sub-Total and then **subtracted** from it to give the Camp Total (`Camp Total = Sub-Total − Recoveries` — `CampReportType.java:335-347` builds the sub-total from five costs with Recoveries absent, and `:357-361` subtracts it). So a recovery is always entered as a **positive** amount. A negative recovery would be subtracted from the total and therefore *increase* it — adding money to the camp cost through a field labelled "Recoveries". On that reading the zero floor is correct and deliberate.

On the other hand, the message name suggests the limit may have been **carried over from Schedule 9** rather than chosen for Schedule 5 — and a borrowed limit that happens to look sensible is still a borrowed limit.

### The question

**Is the zero floor on Recoveries intended for Schedule 5, or inherited from Schedule 9 when it was copied?**

### What we'd suggest

**Keep the zero floor.** The business logic stands up on its own whatever the limit's origin: a negative value would invert what the field means and quietly inflate the camp total. We're flagging the borrowed message only so you can decide with the full picture rather than on our reading of it.

If you'd rather it allowed negatives, it's one limit and one message — the smallest of the four.

> **One line closes this:** *"The zero floor on Recoveries is right — recoveries are positive amounts that get subtracted."*

---

## 4. A valid mill and year with no camps yet

**Today:** a valid, active mill and year that simply has no camps yet opens an **empty Schedule 5**, ready for the first camp to be added. An error is shown only when the mill/year reporting context itself doesn't exist.

### What the existing code shows

The existing application raises "schedule not found" only when its data layer hands back nothing at all:

```java
// ILCRService.java:384-390
public Schedule5DO getSchedule5(Integer reportingYear, Integer millID) throws ILCSException {
    Schedule5DO schedule5 = getSchedule5DAO().getSchedule5(reportingYear, millID);
    if (schedule5 == null) {
        throw new ILCSException(ExceptionCode.SCHEDULE_NOT_FOUND);
    }
    return schedule5;
}
```

And the data layer only hands back nothing when its camp lookup does (`Schedule5DAO.java:63-66`) — but that lookup is a database query list:

```java
// Schedule5DAO.java:303-312
List<CampReport> campReports = findCampReport.list();
return campReports;
```

`Query.list()` returns an **empty list** when there are no rows — never nothing. **So the "no camps yet" case can't reach the error branch at all**: the current application shows an empty camp table, not an error page. Our rebuild does the same — it checks that the mill and year exist (`MillContextService.java:277-292`) and then serves an empty camp list (`Schedule5Service.java:158-161`).

### Why we're asking at all

Our own written requirements for this piece say "no Schedule 5 record → error", which is ambiguous about *which* situation it means, and the difference isn't visible on screen in the current application. We read it as the missing-mill/year case and built accordingly. We'd like that confirmed rather than assumed, because the two readings are the difference between a licensee seeing a blank form and a licensee seeing an error.

### The question

**Can you confirm that a valid mill and year with no camps yet should open an empty Schedule 5, not show an error?**

### What we'd suggest

**Confirm it.** The current application can't produce an error in this situation, and someone starting a fresh reporting year should land on an empty form ready to fill in.

> **One word closes this:** *"Confirmed."*

---

## Where these came from

- **Schedule 10 questions:** #193 — we've batched these four alongside it.
- **Raised during:** #242 and #258.
- **Existing application source referenced above:** `ILCRDistanceValidator.java`, `ILCRCostValidator.java`, `Constants.java:102-109`, `messages.properties:70-74,122`, `schedule5ExistingCamp.xhtml`, `schedule5NewCamp.xhtml`, `ILCRService.java:384-390`, `Schedule5DAO.java:54-66,303-312`, `CampReportType.java:335-347,357-361`.
- **Rebuilt code referenced above:** `backend/.../schedule5/dto/CampRequest.java`, `dto/CategoryEntry.java`, `Schedule5Service.java`, `millcontext/MillContextService.java`.

Thanks very much — we know these are fiddly details, and we appreciate you taking the time.
