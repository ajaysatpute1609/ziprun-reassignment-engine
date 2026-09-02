# ZipRun AI Reassignment Engine — Interview Guide

> One-page, plain-English guide. Read this line-by-line in front of the interviewer.

---

## 1. What is this project? (30 seconds)

**ZipRun AI Reassignment Engine** is a real-time delivery dispatch system. When a delivery agent suddenly goes offline, the engine automatically finds the best replacement agent, explains why, and lets an operator approve or reject the reassignment.

It has:
- A **Spring Boot backend** (business logic, AI, REST API)
- An **Angular frontend** (operator dashboard)
- A **Groq LLM** for AI recommendations with a rule-based fallback

---

## 2. What problem does it solve? (Business logic / use case)

In last-mile delivery, an agent can go offline because of a flat tire, traffic, illness, etc. The orders they were carrying are now stuck. The operations team needs a **fast, explainable, and safe** reassignment.

The system:
1. Detects the offline event.
2. Identifies all stranded orders.
3. Picks the best available agent using AI + business rules.
4. Creates a suggestion with reasoning and confidence.
5. Operator approves/rejects; the order is reassigned.
6. Proactively warns about orders nearing SLA breach before they are late.

**Key business logic:**
- **Load balancing:** Don't overload one agent (`activeOrderCount` vs `maxCapacity`).
- **Capability matching:** Heavy orders go only to agents who can carry them (`canHandleHeavy`).
- **Zone affinity:** Prefer agents already near the pickup zone.
- **SLA awareness:** Warn before a deadline is missed.

---

## 3. Tech stack

### Backend
- **Java 17/21**, **Spring Boot 3.2**
- **Spring Data JPA** with **PostgreSQL**
- **Spring `@Async`** for offline event handling
- **Spring `@Scheduled`** for proactive SLA monitoring
- **RestClient** (Spring 6.1) for Groq LLM calls
- **SSE (Server-Sent Events)** for streaming AI reasoning live

### Frontend
- **Angular 17** standalone components
- **RxJS** polling for live ops board
- **Fetch + ReadableStream** for SSE token streaming

### DevOps / Infra
- **Maven** for backend builds
- **npm** for frontend
- **Docker** for PostgreSQL
- **Groq API** for LLM (`openai/gpt-oss-20b` model)

---

## 4. Database tables and what they mean

| Table | Purpose | Key fields |
|-------|---------|-----------|
| `agents` | Delivery agents/ riders | `id`, `name`, `status` (AVAILABLE/BUSY/OFFLINE), `active_order_count`, `current_zone`, `max_capacity`, `can_handle_heavy` |
| `orders` | Delivery jobs | `id`, `description`, `assigned_agent_id`, `status` (ASSIGNED/REASSIGNED), `sla_deadline`, `pickup_zone`, `dropoff_zone`, `weight_class` |
| `reassignment_suggestions` | Proposed reassignments | `id`, `order_id`, `recommended_agent_id`, `confidence`, `reasoning`, `status` (PENDING/ACCEPTED/REJECTED), `trigger_reason` |

### Logic implemented around these tables
- When an **order is created**, the assigned agent's `active_order_count` goes up.
- When a **suggestion is accepted**, the old agent's load goes down, the new agent's load goes up, and the order status becomes `REASSIGNED`.
- When a **suggestion is rejected**, nothing moves — the order stays as it was.
- `AgentEligibilityFilter` removes agents who are offline, over capacity, or unable to carry heavy orders before any strategy sees them.
- `SlaMonitor` scans `orders` periodically and creates suggestions for orders close to or past their `sla_deadline`.

---

## 5. Core features

1. **Agent status management** — `PATCH /agents/{id}/status` flips an agent OFFLINE.
2. **Agentic re-planning loop** — Offline event triggers async suggestions for every stranded order.
3. **Pluggable routing strategies** — Rule-based, AI-backed, or zone-affinity; switch at runtime with `ROUTING_STRATEGY` env variable.
4. **AI with fallback** — Groq LLM recommends an agent; if it fails or hallucinates, rule-based strategy takes over.
5. **Idempotency** — Flipping the same agent offline twice won't create duplicate suggestions.
6. **Operator approval** — Accept/reject suggestions via the Ops Board.
7. **SLA proactive monitoring** — Scheduled `SlaMonitor` queues suggestions before orders breach SLA.
8. **SSE streaming** — `POST /orders/{id}/suggest/stream` streams the AI's reasoning token-by-token in the UI.
9. **Dispatch dashboard** — Full board with SLA countdown colors (green/amber/red), agent load bars, and zone rosters.

---

## 6. Architecture highlights (why this design is better)

### Strategy pattern for routing
- `RoutingStrategy` interface with implementations: `RuleBasedRoutingStrategy`, `AiRoutingStrategy`, `ZoneAffinityStrategy`.
- `RoutingStrategyResolver` holds a Spring bean map and picks the active one per call.
- **Switch strategy without restart**: `ROUTING_STRATEGY=rule|ai|zone`.
- Adding a new strategy is one class + one annotation — no existing code changes.

### Service layer boundary
- `ReassignmentService` is the single entry point used by both HTTP controller and async event listener.
- Routing logic is isolated from HTTP and event concerns.

### Async + idempotent agentic loop
- `AgentOfflineListener` runs on a dedicated thread pool.
- Checks `existsByOrderIdAndTriggerReasonAndStatus(...)` before creating a suggestion to avoid duplicates.

### Resilient AI
- `AiRoutingStrategy` throws on failure/hallucination.
- `ReassignmentService` catches and falls back to rule-based strategy.

### Proactive SLA loop
- `SlaMonitor` reuses the same `ReassignmentService.suggestForOrder()` — no duplicated logic.
- `RoutingContext` carries `triggerReason` and `slaMinutesRemaining` so the prompt and suggestions are situation-aware.

---

## 7. What makes the product better than a basic reassignment system?

| Capability | Why it matters |
|-----------|----------------|
| **Runtime strategy switching** | Ops can choose rule-based (fast, deterministic) or AI (smart, context-aware) without redeploying. |
| **AI + rule fallback** | You get smart recommendations, but the system never breaks if the LLM is down or wrong. |
| **Explainability** | Every suggestion includes `reasoning` and `confidence`, so operators trust the AI. |
| **Proactive SLA alerts** | The system warns before a breach, not after. |
| **Idempotency + async** | Offline events don't duplicate suggestions or block the UI. |
| **Streaming reasoning** | Operators can watch the AI think live, improving transparency. |
| **Zone + capacity + weight-aware** | Recommendations respect real-world constraints, not just "who is free." |

---

## 8. Explaining `application.properties` line-by-line

Use this when the interviewer points to config:

```properties
# LLM gateway (Groq). Model list is account-specific and changes over time —
# verify with GET https://api.groq.com/openai/v1/models if this 404s.
llm.provider=groq
llm.api-key=${LLM_API_KEY:}
llm.model=openai/gpt-oss-20b
llm.base-url=https://api.groq.com
llm.timeout-ms=8000
```

**What each one does:**
- `llm.provider=groq` — We use Groq as the LLM provider.
- `llm.api-key=${LLM_API_KEY:}` — API key is injected from an environment variable so secrets never sit in code.
- `llm.model=openai/gpt-oss-20b` — The model we call. Groq models change per account, so we made it configurable.
- `llm.base-url=https://api.groq.com` — Groq's base endpoint.
- `llm.timeout-ms=8000` — LLM calls cap at 8 seconds so a slow LLM cannot hang the whole reassignment flow.

```properties
management.endpoints.web.exposure.include=health,info
```
- Exposes Spring Boot Actuator endpoints `/actuator/health` and `/actuator/info` for health checks and basic app metadata.
- Useful for deployment monitoring and Kubernetes liveness/readiness probes.

```properties
# Sprint 3 — proactive SLA-breach monitor (SlaMonitor). Checks every
# sla.check.interval-ms for ASSIGNED orders within sla.risk.threshold-minutes
# of their deadline (or already past it) and queues a proactive suggestion.
# Interval is short (30s) for demo purposes; a production deployment would
# likely use a longer interval.
sla.check.interval-ms=30000
sla.risk.threshold-minutes=10
```
- `sla.check.interval-ms=30000` — `SlaMonitor` runs every 30 seconds.
- `sla.risk.threshold-minutes=10` — Any ASSIGNED order within 10 minutes of its SLA deadline (or already past it) gets a proactive `ReassignmentSuggestion`.
- In production you would increase the interval to minutes, not seconds.

```properties
routing.strategy=${ROUTING_STRATEGY:rule}
```
- Decides which `RoutingStrategy` bean is active.
- Default is `rule` (rule-based).
- Can be overridden at runtime by environment variable, e.g. `ROUTING_STRATEGY=ai` or `ROUTING_STRATEGY=zone`, without changing code or restarting.

---

## 9. Quick demo narrative (use if they ask "show me how it works")

1. Open `http://localhost:4200` → Ops Board shows agents, orders, pending suggestions.
2. Click **Flip Offline** on an agent with active orders.
3. Within seconds, suggestions appear with AI reasoning and confidence.
4. Click **Accept** → order moves to `REASSIGNED` and the new agent's load bar updates.
5. Click **Flip Offline** again on the same agent → no duplicate suggestions (idempotency).
6. Switch to **Full Dispatch Board** → see SLA countdowns (green/amber/red) and agent load bars.
7. Click **Stream AI Suggestion** to watch the LLM reasoning arrive token-by-token.

---

## 10. Sprint summary (if they ask about scope)

- **Sprint 1 (main branch, submitted):** Core reassignment engine — agents, orders, suggestions, rule-based and AI routing, async offline listener, operator board.
- **Sprint 2 (sprint-2-extensions branch):** Zone affinity strategy, capacity/weight eligibility, SLA proactive monitoring, SSE streaming, and the `activeOrderCount` bugfix so load numbers stay accurate.
