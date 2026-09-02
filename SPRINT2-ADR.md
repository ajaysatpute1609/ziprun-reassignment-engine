# Sprint 2 Extension — Architecture Decision Record

This file documents decisions made on the `sprint-2-extensions` branch only.
It is **not merged into `main`**, which remains the submitted assessment
snapshot. See `ADR.md` on `main` for the original five decisions.

---

## SPRINT2-ADR-1: Where do capacity and weight-class eligibility rules live?

**Context.** Sprint 2 adds two hard constraints: no agent should ever be
recommended past `maxCapacity`, and a `HEAVY` order should never be
recommended to an agent with `canHandleHeavy = false`. These rules need to
apply no matter which `RoutingStrategy` is active — rule-based, AI, or the
new zone-affinity strategy — so a future strategy author can't accidentally
forget to enforce them.

**Options considered.**
(a) Enforce the checks inside every `RoutingStrategy` implementation —
correct per-strategy, but duplicated logic, and a new strategy (like this
sprint's `ZoneAffinityStrategy`) could easily forget to check it.
(b) Enforce them as a pre-filter in `ReassignmentService`, before any
strategy ever sees the agent list — one place, impossible to bypass by
accident from within a strategy.
(c) Enforce them as a database-level constraint/query — technically
possible for capacity, but weight-class eligibility depends on the specific
order being routed, which doesn't fit a static query well.

**Decision.** Chose (b): a new `AgentEligibilityFilter` component, called
once inside `ReassignmentService.suggestForOrder()` right after loading
`AVAILABLE` agents and before calling the active strategy (and before the
fallback strategy too, since both receive the already-filtered list). This
keeps "who is allowed to be considered at all" (a business rule) separate
from "who is best among the eligible ones" (a strategy's judgment call) —
the same Service Layer boundary established in `ADR-1` on `main`.

**Tradeoffs accepted.** If eligibility filtering removes every candidate,
`suggestForOrder()` now throws before even reaching a strategy — callers
need to handle "no eligible agent" as a distinct failure mode from "the AI
strategy failed." This is a slightly coarser signal than, say, having each
strategy report why it excluded specific agents, but it's simpler and keeps
the constraint centralized rather than distributed.

---

## SPRINT2-ADR-2: How does `ZoneAffinityStrategy` plug in without touching existing code?

**Context.** The sprint 1 ADR (`ADR-2` on `main`) predicted this exact
scenario: a third routing strategy needs to be addable by "implementing the
interface and registering the bean — no changes to selection logic."

**Options considered.** Same three options as `ADR-2` on `main` (Qualifier,
bean map, manual factory) — already decided in favor of the bean map on
`main`, so this decision is really "does that design hold up in practice."

**Decision.** `ZoneAffinityStrategy implements RoutingStrategy`, annotated
`@Component("zone")`. To activate it: `ROUTING_STRATEGY=zone` (or
`routing.strategy=zone` in properties) — no restart, no other file touched.
Verified: `RoutingStrategyResolver`, `ReassignmentService`,
`AgentOfflineListener`, and `OrderController` are all byte-for-byte
unchanged from `main` for this strategy to work. The only shared-code change
this sprint required was `AgentEligibilityFilter` (SPRINT2-ADR-1), which is
a cross-cutting rule, not routing-strategy-specific wiring.

**Tradeoffs accepted.** `ZoneAffinityStrategy` falls back to pure load-based
ordering when either the order or the candidate agents lack zone data
(nullable fields), rather than excluding un-zoned agents. This is a
deliberate leniency — a stricter version could require zone data to be
present — but it means zone affinity is a soft preference, not a hard
constraint, which matches the brief's framing ("routing starts *preferring*
agents already near the pickup zone").

---

## SPRINT2-ADR-3: How does the AI strategy learn about zone/weight without a new prompt structure?

**Context.** `AiRoutingStrategy` and `PromptBuilder` already existed from
sprint 1 with two prompt shapes (initial vs. re-plan). Sprint 2 needed the
AI to know about zones, capacity, and weight class too, without duplicating
the whole prompt-building logic a third time.

**Options considered.**
(a) A third, separate prompt template for "sprint 2 aware" routing —
duplicates most of the existing prompt text for a small delta.
(b) Extend the existing roster-formatting and order-description logic
inside `PromptBuilder` to include the new fields when present, leaving the
two prompt *shapes* (initial vs. re-plan) untouched.

**Decision.** Chose (b). `formatRoster()` now appends zone, capacity, and
heavy-carry capability per agent when those fields are non-null;
`formatOrderMeta()` appends pickup/dropoff zone and weight class to the
order description. Both existing prompt templates (initial, re-plan) gained
one instruction line each ("do not recommend an agent who cannot carry
HEAVY..."). No new prompt shape — the situational difference between
initial and re-plan (AGT-3 on `main`) stays the axis that matters; zone/
weight are just more facts within whichever situation applies.

**Tradeoffs accepted.** Because `AgentEligibilityFilter` already removes
ineligible agents before the AI ever sees them (SPRINT2-ADR-1), the prompt
instruction about not recommending HEAVY-incapable agents is technically
redundant — those agents are never in the roster the AI sees. It's kept
anyway as defense-in-depth: if eligibility filtering is ever bypassed or
has a bug, the prompt is a second, independent guard, and it costs nothing
to include.

---

## SPRINT2-ADR-4: How does the proactive SLA-breach loop (sprint 3 preview) fit without restructuring the agentic loop?

**Context.** `main`'s `ADR-5` explicitly named this as the thing to be able
to point at: "is your event mechanism general enough to accept a new
trigger type? Is your routing interface as comfortable being called from a
scheduled monitor as from an event handler?" This entry answers that for
real, not hypothetically — the proactive loop is built on this branch.

**Options considered.**
(a) Duplicate the reassignment pipeline for the SLA case — a separate
`SlaReassignmentService` with its own suggestion-creation logic — keeps the
two triggers fully independent but means any future change to fallback
behavior, idempotency, or persistence has to be made twice.
(b) Reuse `ReassignmentService.suggestForOrder()` as-is, feeding it a new
`RoutingContext.slaAtRisk(minutesRemaining)`, with a new `SlaMonitor`
`@Scheduled` component as the only new piece of trigger *mechanism*.
(c) Extend `AgentOfflineEvent`/`AgentOfflineListener` to also mean "any
reassignment trigger," renaming them generically — technically works, but
conflates two conceptually different mechanisms (react-to-event vs.
poll-on-a-timer) into one class, hurting readability.

**Decision.** Chose (b). `SlaMonitor` is a new `@Scheduled(fixedRateString
= "${sla.check.interval-ms}")` component, structurally parallel to
`AgentOfflineListener` (observe → reason → act → checkpoint) but triggered
by a clock instead of an event. It queries `OrderRepository.findByStatus(ASSIGNED)`,
computes minutes remaining against `Order.slaDeadline`, and for any order
within `sla.risk.threshold-minutes` (or already breached), calls the exact
same `ReassignmentService.suggestForOrder()` used by both the HTTP
`/suggest` endpoint and `AgentOfflineListener` — zero changes to that
method, to `RoutingStrategyResolver`, or to any `RoutingStrategy`
implementation. The only *new* production code is `SlaMonitor` itself, the
`SLA_AT_RISK` enum value, and a third field on `RoutingContext`
(`slaMinutesRemaining`) plus a third prompt shape in `PromptBuilder`. The
idempotency guard is the same repository method used for
`AGENT_OFFLINE` (`existsByOrderIdAndTriggerReasonAndStatus`), just called
with a different `TriggerReason` — no new idempotency logic needed.

**Tradeoffs accepted.** A scheduled poll is inherently less immediate than
an event — an order could sit up to `sla.check.interval-ms` (30s in this
config) past crossing the risk threshold before being noticed, whereas
`AgentOfflineListener` reacts within milliseconds of the status change.
For an SLA measured in tens of minutes, a 30-second polling granularity is
an acceptable tradeoff for the simplicity of not building a delay-queue or
timer-per-order mechanism. This is also the one case in the whole system
where a scheduled poller is the *right* tool (contrast with `main`'s
`ADR-4`, which explicitly rejected polling for the OFFLINE trigger) —
there's no discrete event to react to when the thing that changed is just
elapsed time.
