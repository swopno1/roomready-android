# Verification — RoomReady (P10, refreshed for the production shape)

**Date:** November 15, 2026
**Scope:** verification only at P10; refreshed after Demo Mode removal
(seed test deleted, `ProductionShapeTest` added covering empty state,
first-sync roster, disconnect warning, upgrade purge, and migration
8→9).
**Headline:** 125/125 unit tests pass across 19 suites, lint 0 errors
(34 warnings), `assembleDebug` succeeds.

---

## Static checks

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | State-machine legality: every illegal transition rejected, all roles | PASS | `RoomTransitionEngineTest.full matrix every illegal transition rejected for every role` — all 75 (from × to × role) combos asserted; 53 illegal rejected, 22 legal allowed. `listed actions match legality for every status and role` — `legalActionsFor` agrees with `isLegalTransition` on all 15 (status × role) sets. Repo enforcement: `InspectionFlowTest.return requires a reason…` (illegal DIRTY→READY fails at `transitionRoomStatus`) |
| 2 | Checklist rules: finish at 8/8, stayover template | PASS | `ChecklistRunFlowTest.finish at 8 of 8 stamps run moves to cleaned and logs`, `finish blocked at 7 of 8`, `stayover selectable on occupied room creates 4-step run`, `stayover rejected on vacant room`, `run survives process death via room store`; `ChecklistTemplatesTest.full template has 8 steps in canonical order`, `stayover template has exactly the 4 light steps`, `finish allowed only at full completion`; run-screen UI tests at 7/8 (Finish disabled) and 8/8 (enabled) |
| 3 | Ticket lifecycle | PASS | `TicketFlowTest.create done reopen cycle records who and when`, `urgent badge count tracks open urgent tickets`, `ticket detail shows done action for open ticket`; `TicketStatsTest.urgent open count ignores done and non-urgent`, `urgent open by room groups correctly`, `filter by status` |
| 4 | Dashboard counts vs fixture | PASS | `DashboardFlowTest.status counts match seeded fixture` (3/3/2/3/1 across the 5 statuses), `cleans today counts transitions since midnight with per-person list`, `open tickets group by priority and stuck rooms surface`; `DashboardStatsTest` (7 pure-count tests incl. `stuck rule is cleaning older than 60 minutes`) |
| 5 | Sync merge matrix (P7) | PASS | `SyncEngineTest.merge matrix respects in-progress cleans and timestamps`, `local newer cleaning wins over stale server state`, `push order follows outbox and bodies match contract`, `retry after failure keeps entry and drains on success`, `401 wipes the key and signals auth revoked`, `409 drops the entry and logs the conflict`, `bootstrap replaces snapshot wholesale and ignores unknown fields`, `bootstrap applies rename add and archive`, `bootstrap preserves in-progress cleans and outbox`, `contract version mismatch changes nothing`, `malformed bootstrap leaves state untouched`, `disconnect wipes key and clears the whole snapshot` (14 tests, MockWebServer fake server) |
| 6 | Language completeness | PASS | `LanguageCompletenessTest` 4/4: bn covers every en key (240/240, zero missing/extra), zero blanks, placeholder parity, zero untranslated outside the documented allowlist (brand, formats, native-script language names) |
| 10 | Production shape (no demo) | PASS | `ProductionShapeTest` 7/7: unconfigured empty reads, purge empties tables but keeps photos/settings, configured upgrade untouched, first sync fills roster + name persists, disconnect warning shown/skipped, migration 8→9 drops `isDemo` and keeps rows |
| 7 | 1.3× font-scale rendering | PASS | `LowLiteracyUiTest.font scale 1_3 keeps board nodes displayed`, `font scale 1_3 keeps detail actions reachable` (Density override + scroll + `assertIsDisplayed`); plus `detail primary action sits in the bottom two thirds` (measured top fraction > ⅓ viewport) |
| 8 | Airplane-mode pass | PASS | `AirplaneModeTest.airplane mode still runs every core flow` — proves the condition first (`AIRPLANE_MODE_ON=1`, `activeNetworkInfo=null`), then runs fixture start → 8 steps → skip-photo → finish → inspect-return → ticket create/resolve → OOS-guard check → dashboard reads, all succeeding. `unconfigured transport stays silent on all six endpoints` — unconfigured (null URL/key) providers return `NotConfigured` on bootstrap/refresh/postStatus/getOpenTickets/postTicket/patchTicket without opening a socket |
| 9 | Cold start on a 2 GB profile | NOT MEASURED HERE | Plainly: no device or emulator is reachable from this environment (adb daemon cannot start in the sandbox), so no cold-start timing was taken. Proxy facts: single-activity app, lazily-built container, no FCM/push SDKs, no third-party content providers (FileProvider only). Measure on-device with `adb shell am start -W` before release if a number is required |

## Blockers

None. Zero failures across 125 tests; no crash was exposed, so no
product fix was applied.

## Dynamic alignment checks (need the live W2 PMS)

Not runnable here — no PMS exists in this environment. Founder steps
with the PMS admin web dashboard (narrated clicks):

| # | Check | Steps | Expected |
|---|---|---|---|
| D1 | New unit appears | (a) In the PMS admin, add a new unit under any room type. In the app: Me → PMS Connection → Sync now | The unit appears on the board with the PMS number/type/status |
| D2 | Type rename applies | (b) In the PMS admin, rename a room type. Sync the app | The new title shows on every unit of that type |
| D3 | Archived unit disappears | (c) In the PMS admin, archive a unit. Sync the app | The unit is gone from the board (unless a clean is in progress on it, which is preserved) |
| D4 | Checklist edit applies | (d) In PMS Settings, edit the housekeeping checklist (add one step). Sync the app | New runs on that type show the added step |
| D5 | Revoked key lands honestly | Revoke the app's key in the PMS, then Sync now | The app wipes the stored key, navigates to the Connection screen with the "key was rejected" message, and keeps all local data untouched |

Record each result here (PASS/FAIL + observed app behavior) when run.
