# Architecture Decision Record — ZipRun AI Reassignment Engine

Each entry: Context → Options considered → Decision → Tradeoffs accepted.
Entries are written as decisions are made during the build, not retrospectively.

---

## ADR-1: Where does routing logic live?

**Context.** Choosing an agent for an order needs decision logic that will
grow in complexity (rule-based today, AI-backed shortly, zone/capacity-aware
in sprint 2). It's called from an HTTP endpoint and, in T-4, from an async
event handler. It needs a home that isolates "how do we pick an agent" from
"how does an HTTP request or an event turn into a picked agent."

**Options considered.**
(a) Put the logic directly in the controller — fastest to write, but couples
HTTP concerns to decision logic and can't be reused from the async listener
without duplicating code.
(b) Put it on the domain entities (`Order.suggestAgent(...)`) — keeps it
"in the model," but an entity reaching out to an LLM or querying other
agents' load breaks the persistence-focused role of an entity and makes
testing awkward.
(c) A dedicated `routing` package with a `RoutingStrategy` interface,
concrete strategies, and a resolver, called by a thin `ReassignmentService`
application service that both the controller and the event listener depend
on.

**Decision.** Chose (c). The `RoutingStrategy` interface + implementations
live in `com.ziprun.reassignment.routing`; a `ReassignmentService` (Service
Layer / Application Service pattern) orchestrates "load agents → call the
active strategy → persist a suggestion" and is the single entry point used
by both `SuggestionController` (T-2's `/suggest` endpoint) and the async
`AgentOfflineListener` (T-4). Neither caller talks to a `RoutingStrategy`
directly.

**Tradeoffs accepted.** One more layer of indirection than putting logic
straight in the controller — a reader has to follow controller → service →
resolver → strategy to see the actual decision. Accepted because it's what
keeps the two callers (sync HTTP, async event) from diverging, and keeps
`ReassignmentService` from becoming the kind of service that quietly
accumulates routing logic, event publishing, and persistence all in one
untestable place — each of those stays in its own class.

---

## ADR-2: How does runtime strategy switching work?

**Context.** The routing engine supports a rule-based strategy today and an
AI strategy next (T-3), and the active one needs to be switchable without a
restart. Both `ReassignmentService` call sites (HTTP endpoint, async event
handler) must see the same active strategy. Sprint 2 adds a third
(`ZoneAffinityStrategy`), so the wiring must accommodate a new strategy
without touching existing code.

**Options considered.**
(a) Spring `@Qualifier` with a config property — straightforward but
requires a restart to switch and doesn't scale cleanly to a third strategy.
(b) Auto-wired `Map<String, RoutingStrategy>` bean map — Spring populates the
map automatically from every bean implementing the interface, keyed by bean
name; the active one is selected at call time by reading a config property.
(c) A manual factory with a switch statement — explicit, but requires
editing the factory every time a strategy is added.

**Decision.** Chose (b): `RoutingStrategyResolver` holds the injected
`Map<String, RoutingStrategy>` and reads `routing.strategy` (default `rule`,
overridable via environment variable, e.g. `ROUTING_STRATEGY=ai`) on every
call to `active()` — not cached at startup — so it's switchable without a
restart. Adding `ZoneAffinityStrategy` in sprint 2 means implementing the
interface and annotating the bean `@Component("zone")`; zero changes to the
resolver, the service, or either caller. A `@PostConstruct` check fails fast
at startup if the configured name isn't a registered bean, rather than
failing on the first request.

**Tradeoffs accepted.** The bean-map approach is slightly less explicit than
a factory — a reader needs to know Spring auto-populates the map by bean
name to understand where strategies "come from." We also lose compile-time
guarantees that `routing.strategy` names a real strategy; a misconfiguration
is a runtime failure, mitigated (but not eliminated) by the startup check.

---

## ADR-3: How does the system stay resilient when the LLM is unavailable?

**Context.** `AiRoutingStrategy` calls Groq over HTTP. That call can fail in
several distinct ways: network timeout, HTTP/quota error, a response that
isn't valid JSON, or valid JSON that recommends an agent id that doesn't
exist or isn't currently available (a hallucination). The system must stay
healthy in every case — especially in the async re-plan path (T-4), where a
silently dropped suggestion is worse than a rule-based one.

**Options considered.**
(a) Let failures propagate to the HTTP caller as a 500 — simplest to write,
but means a flaky LLM call breaks the reassignment flow entirely, including
the async re-plan where there's no HTTP caller to show an error to.
(b) Catch failures inside `AiRoutingStrategy` itself and return a rule-based
recommendation directly — keeps the caller ignorant of any failure, but
blurs what "the AI strategy" actually did and makes it hard to log/monitor
AI failure rates distinctly from rule-based usage.
(c) Let `AiRoutingStrategy` throw on any failure mode (never swallow into a
fabricated success), and have the single caller — `ReassignmentService` —
catch and fall back to the rule-based strategy, logging what happened.

**Decision.** Chose (c). `AiRoutingStrategy.recommend()` throws
`IllegalStateException` for a hallucinated/unavailable agent id or an
unparseable response, and lets `RestClient`'s own exceptions (timeout, HTTP
error) propagate. `ReassignmentService.suggestForOrder()` wraps the active
strategy call in a try/catch and falls back to
`RoutingStrategyResolver.fallback()` (always the rule-based strategy) on any
exception, logging the trigger reason and the failure message. Because both
the HTTP `/suggest` endpoint and the async `AgentOfflineListener` both go
through `suggestForOrder()`, this one fallback path covers both callers —
the async re-plan always produces a suggestion, never a silent drop.
Timeouts are bounded explicitly (`llm.timeout-ms`, default 8s) via a
`SimpleClientHttpRequestFactory` so a hung request can't stall the executor
pool indefinitely.

**Tradeoffs accepted.** The fallback is coarse — any AI failure, regardless
of cause, degrades to the same rule-based recommendation rather than, say,
retrying once for a transient timeout. That's a deliberate simplicity
tradeoff for a 5-hour build; a production version would likely distinguish
retryable failures (timeout) from non-retryable ones (hallucinated id) and
only retry the former.

**Verified live.** Tested against the real Groq API: an initial
misconfigured model name produced a `404 model_not_found` from Groq, which
was caught and logged by `ReassignmentService`, and the endpoint still
returned a valid rule-based suggestion rather than an error — confirming the
fallback path works against real failures, not just mocked ones. After
fixing the model name, both the initial-assignment and agent-offline re-plan
prompts were confirmed to produce genuinely different, situation-appropriate
reasoning text (see git history / demo video for the actual responses).

---

## ADR-4: How is the agentic loop triggered and kept off the request path?

**Context.** `PATCH /agents/{id}/status` must return immediately even when
the new status is `OFFLINE`, at which point potentially several orders need
re-planning — each involving an LLM call. The re-planning must not run on
the request thread, and the mechanism needs to be idempotent (AGT-4): the
same agent flipping offline twice should not create duplicate suggestions.

**Options considered.**
(a) A scheduled poller that periodically scans for `OFFLINE` agents with
un-replanned orders — decouples nicely but reacts to a timer tick, not to
the actual state change; the brief explicitly calls this out as not quite
right for this use case, and it adds latency between the event and the
reaction.
(b) `ApplicationEventPublisher.publishEvent(...)` inside the PATCH handler,
consumed by an `@EventListener` method annotated `@Async` — the publish call
returns immediately (Spring's default `ApplicationEventPublisher` behavior
is synchronous dispatch to listeners, but an `@Async` listener hands off to
a separate thread pool before doing any work, so the publishing thread isn't
blocked by the listener's body).
(c) A dedicated `ExecutorService` invoked directly from the controller —
similar effect to (b) but couples the controller to threading concerns
directly instead of leaving that to the event/listener abstraction.

**Decision.** Chose (b). `AgentController.updateStatus()` publishes an
`AgentOfflineEvent` and returns the updated `Agent` immediately.
`AgentOfflineListener.onAgentOffline()` is `@EventListener` + `@Async` on a
dedicated `reassignmentExecutor` thread pool (`AsyncConfig`), so it runs
after the controller's response has already been sent. Inside the listener:
find all orders currently assigned to the offline agent, and for each one,
check `ReassignmentSuggestionRepository.existsByOrderIdAndTriggerReasonAndStatus(orderId, AGENT_OFFLINE, PENDING)`
before creating a new suggestion — the idempotency guard. A failure
re-planning one order is caught and logged without aborting the loop for
the remaining orders.

**Tradeoffs accepted.** `@Async` event listeners lose the calling thread's
transaction context and any exception thrown inside the listener is not
visible to the original HTTP caller — it's caught and logged internally
instead, which means ops has no direct signal if the *entire* re-plan loop
throws (as opposed to a single order within it, which is handled). For a
5-hour build this is accepted; a production version would likely add a
dead-letter/retry mechanism or a monitoring alert on listener-level
failures.

---

## ADR-5: What did you design to extend, and what did you deliberately leave for later?

**Extensibility seam.** Sprint 2's `ZoneAffinityStrategy` plugs in at exactly
one point: implement `RoutingStrategy.recommend(Order, List<Agent>,
RoutingContext)` and annotate the class `@Component("zone")`. Nothing in
`RoutingStrategyResolver`, `ReassignmentService`, `OrderController`, or
`AgentOfflineListener` changes — the resolver's injected
`Map<String, RoutingStrategy>` picks it up automatically, and switching to it
is a one-line config change (`routing.strategy=zone` / `ROUTING_STRATEGY=zone`
env var), no restart. The `Agent.currentZone` field already exists (nullable,
unused by the two current strategies) specifically so this sprint 2 change is
additive rather than a migration — `ZoneAffinityStrategy` just starts reading
a column that's already there. The same seam is why sprint 3's proactive
SLA-breach loop is straightforward to add on the trigger side too: it would
publish a new event type (e.g. `SlaBreachImminentEvent`) consumed by a
listener that calls the exact same `ReassignmentService.suggestForOrder()`
used today — the event mechanism doesn't care whether the trigger was an
agent going offline or a scheduled SLA monitor, because `RoutingContext`
already models "why is this suggestion happening" as data, not as a fixed
enum tied to one scenario.

**Deliberate exclusions.**
- **SSE streaming (`/orders/{id}/suggest/stream`) — not built.** This is a
  pure UX enhancement (watching tokens arrive) with zero effect on
  correctness; the agentic loop and its fallback behavior are a correctness
  requirement and got the time instead.
- **UI ceiling (full dispatch board, SLA countdown, agent load chart, zone
  roster) — not built.** The brief is explicit that this is primarily a
  backend/systems design screen; a clean, fully-working floor (reassignment
  queue, badges, accept/reject, agent roster, polling, loading/error states)
  demonstrates the agentic loop end-to-end, which is the thing actually being
  evaluated. An ambitious but partially-working ceiling would have traded
  against backend robustness for a lower-weighted area.
- **Capacity/weight-class constraints (sprint 2) — not built.** Both current
  strategies assume any available agent can take any order. Adding
  `Agent.maxCapacity` and `Order.weightClass` now would be pure speculative
  schema without a strategy that reads them yet, which is exactly the kind of
  premature complexity the brief warns against — better to add the column
  when the strategy that needs it exists.
