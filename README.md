# ZipRun AI Reassignment Engine

> **Real-time, AI-assisted delivery dispatch engine that keeps orders moving when agents go offline.**

A production-style Spring Boot backend with an Angular dashboard. It detects stranded orders, recommends the best replacement agent using pluggable routing strategies (rule-based, AI, zone-affinity), explains the decision, and lets an operator accept or reject — all with proactive SLA monitoring and live AI reasoning streaming.

---

## Demo Video

A complete end-to-end walkthrough is available here:

**Link:** [ZipRun AI Reassignment Engine — Demo Video](https://drive.google.com/file/d/1NpqjxEEKqaMED3gzhRpJ7PrA3-ueYI-s/view?usp=drive_link)

This video demonstrates:
- The Angular ops board with live agent load bars and SLA countdowns
- Flipping an agent offline and watching the agentic re-plan loop create suggestions
- Accepting / rejecting reassignments and load counts updating in real time
- Switching routing strategies (`rule`, `ai`, `zone`) at runtime
- Proactive SLA-breach suggestions appearing automatically
- SSE streaming of the AI's reasoning token-by-token

---

## Table of Contents

1. [Demo Video](#demo-video)
2. [Problem & Business Value](#problem--business-value)
3. [Core Features](#core-features)
4. [Tech Stack](#tech-stack)
5. [Architecture & Design Decisions](#architecture--design-decisions)
6. [Business Logic & Domain Rules](#business-logic--domain-rules)
7. [Routing Strategies](#routing-strategies)
8. [LLM Integration](#llm-integration)
9. [Database Schema](#database-schema)
10. [API Reference](#api-reference)
11. [Getting Started](#getting-started)
12. [Configuration & Environment Variables](#configuration--environment-variables)
13. [Demo Flow](#demo-flow)
14. [Why This Product is Strong](#why-this-product-is-strong)
15. [Future Roadmap](#future-roadmap)
16. [Branch Note](#branch-note)

---

## Problem & Business Value

In last-mile logistics, a delivery agent can suddenly become unavailable — bike breakdown, traffic accident, illness, or network loss. The orders they carry are now **stranded**. Every minute of delay damages customer trust and incurs SLA penalties.

**ZipRun removes manual fire-fighting:**

| Business Pain | How ZipRun Solves It |
|---------------|----------------------|
| Manual reassignment is slow and error-prone | Async agentic loop proposes replacements in seconds |
| One offline agent stalls multiple orders | All stranded orders are re-planned as a batch |
| Overloading a single replacement | Load + capacity rules prevent over-assignment |
| Wrong vehicle for heavy items | Weight-class eligibility enforces capability |
| SLA breaches discovered too late | Proactive SLA monitor creates suggestions before breach |
| Black-box AI recommendations | Every suggestion includes `confidence` and `reasoning` |
| LLM outages break operations | Rule-based fallback is always ready |

### What the product stands for

- **Resilience:** graceful degradation when AI or network fails.
- **Explainability:** auditable routing decisions with reasoning.
- **Extensibility:** new routing strategies plug in without touching existing code.
- **Proactivity:** acts on SLA risk before it becomes a breach.

In short: *an intelligent, human-in-the-loop reassignment copilot for delivery operations.*

---

## Core Features

1. **Agent status management** — `PATCH /agents/{id}/status` flips an agent `OFFLINE`.
2. **Async agentic re-planning** — offline event triggers suggestions for every stranded order on a dedicated thread pool.
3. **Pluggable routing strategies** — `rule`, `ai`, `zone`; switch at runtime with `ROUTING_STRATEGY`.
4. **AI with deterministic fallback** — Groq LLM recommends; if it fails or hallucinates, rule-based strategy takes over.
5. **Idempotency** — flipping the same agent offline twice never creates duplicate suggestions.
6. **Capacity reservation** — a `PENDING` suggestion immediately reserves the recommended agent’s load so the next stranded order is routed elsewhere.
7. **Operator approval** — accept/reject suggestions; accept moves the order, reject releases the reservation.
8. **Proactive SLA monitoring** — `SlaMonitor` scans `ASSIGNED` orders and queues suggestions before deadlines.
9. **SSE streaming** — `POST /orders/{id}/suggest/stream` streams the LLM’s reasoning token-by-token.
10. **Dispatch dashboard** — Angular ops board with SLA countdown colors, agent load bars, and zone rosters.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.2, Spring Data JPA |
| Database | PostgreSQL |
| Async / Events | Spring `@Async`, `ApplicationEventPublisher` |
| Scheduling | Spring `@Scheduled` (SLA monitor) |
| LLM | Groq (`openai/gpt-oss-20b`) via Spring `RestClient` |
| Streaming | Server-Sent Events (SSE) |
| Frontend | Angular 17, standalone components, RxJS |
| Build | Maven (backend), npm (frontend) |
| Infra | Docker for PostgreSQL |

---

## Architecture & Design Decisions

### Decision 1 — Where does routing logic live?

**Context:** Routing will grow from rule-based to AI-backed to zone/capacity-aware. It is called from an HTTP endpoint and from an async event listener. It needs a home that isolates "how to pick an agent" from "what triggered the pick."

**Options considered:**
- (a) Put logic in the controller — fast but couples HTTP to decision logic and cannot be reused by the async listener.
- (b) Put logic on domain entities (`Order.suggestAgent(...)`) — keeps it in the model, but entities calling an LLM or querying other agents is awkward and hard to test.
- (c) Dedicated `routing` package with a `RoutingStrategy` interface + a thin `ReassignmentService` orchestration layer called by both HTTP and async triggers.

**Decision:** Chose (c). `RoutingStrategy` implementations live in `com.ziprun.reassignment.routing`; `ReassignmentService` is the single entry point used by `OrderController` and `AgentOfflineListener`. Neither caller talks to a strategy directly.

**Tradeoff:** One more layer to trace, but it keeps sync HTTP and async event callers from diverging and prevents `ReassignmentService` from accumulating routing, event, and persistence logic in one untestable class.

### Decision 2 — How does runtime strategy switching work?

**Context:** The active routing strategy must be switchable without a restart, and adding a new strategy must not require editing existing code.

**Options considered:**
- (a) Spring `@Qualifier` with a config property — requires restart and does not scale to many strategies.
- (b) Spring-injected `Map<String, RoutingStrategy>` bean map, keyed by bean name, with `routing.strategy` read at call time.
- (c) Manual factory with a switch statement — explicit but requires editing the factory for every new strategy.

**Decision:** Chose (b). `RoutingStrategyResolver` injects the bean map and resolves `routing.strategy` (default `rule`) on every call. Adding `ZoneAffinityStrategy` was one class annotated `@Component("zone")`; zero changes to the resolver, service, or callers.

**Tradeoff:** Slightly less explicit wiring; a reader must understand Spring populates the map by bean name. A `@PostConstruct` check fails fast if the configured name is not registered.

### Decision 3 — How does the system stay resilient when the LLM is unavailable?

**Context:** `AiRoutingStrategy` calls Groq over HTTP. Failures include timeout, quota error, malformed JSON, or a hallucinated agent id. The async re-plan path must never silently drop a suggestion.

**Options considered:**
- (a) Let failures propagate as 500 — breaks the reassignment flow entirely.
- (b) Swallow failures inside the AI strategy and return a rule-based recommendation — hides AI failure from monitoring.
- (c) Let the AI strategy throw on any failure, and let `ReassignmentService` catch and fall back to the rule-based strategy with logging.

**Decision:** Chose (c). `AiRoutingStrategy` throws on hallucination or bad response. `ReassignmentService.suggestForOrder()` catches it and calls `RoutingStrategyResolver.fallback()` (always rule-based). This one fallback path covers both HTTP `/suggest` and async re-plan. Timeout is bounded by `llm.timeout-ms`.

**Tradeoff:** Coarse fallback — any AI failure degrades to rule-based. A production version would distinguish retryable (timeout) from non-retryable (hallucinated id) failures.

### Decision 4 — How is the agentic loop triggered and kept off the request path?

**Context:** `PATCH /agents/{id}/status` must return immediately even when an agent goes `OFFLINE`, because multiple orders may need re-planning and each may involve an LLM call. The same agent flipping offline twice must not create duplicate suggestions.

**Options considered:**
- (a) Scheduled poller — decoupled but reacts to a timer, not the state change; adds latency.
- (b) `ApplicationEventPublisher` + `@Async` `@EventListener` — event publisher returns immediately, listener runs on a dedicated thread pool.
- (c) Dedicated `ExecutorService` directly in the controller — similar effect but couples controller to threading.

**Decision:** Chose (b). `AgentController.updateStatus()` publishes `AgentOfflineEvent` and returns. `AgentOfflineListener` is `@EventListener` + `@Async` on `reassignmentExecutor`. For each stranded order it checks `existsByOrderIdAndTriggerReasonAndStatus(...)` before creating a suggestion. Listener-level failures are caught and logged without aborting the rest of the batch.

**Tradeoff:** Exceptions in the async listener are not visible to the HTTP caller. A production deployment would add a dead-letter/retry mechanism or alert on listener failures.

### Decision 5 — Where do capacity and weight-class rules live?

**Context:** Sprint 2 added `maxCapacity` and `canHandleHeavy`. These rules must apply no matter which strategy is active, and a future strategy author must not be able to forget them.

**Options considered:**
- (a) Enforce inside every strategy — duplicated and easy to miss.
- (b) Central `AgentEligibilityFilter` pre-filter called before any strategy sees the agent list.
- (c) Database-level constraint — possible for capacity but weight-class eligibility depends on the specific order.

**Decision:** Chose (b). `AgentEligibilityFilter` is called in `ReassignmentService.suggestForOrder()` right after loading agents and before the active (and fallback) strategy. It removes agents that are over capacity or cannot carry the order weight class.

**Tradeoff:** If filtering removes every candidate, `suggestForOrder()` throws before reaching a strategy. Callers must handle "no eligible agent" as a distinct failure mode from "AI strategy failed."

### Decision 6 — How does the proactive SLA-breach loop fit without restructuring the agentic loop?

**Context:** The system must also react when an order is nearing its SLA deadline, not just when an agent goes offline. The routing pipeline should be reused, not duplicated.

**Options considered:**
- (a) Separate `SlaReassignmentService` — independent but duplicates fallback and idempotency logic.
- (b) Reuse `ReassignmentService.suggestForOrder()` with a new `RoutingContext.slaAtRisk(minutesRemaining)` and a new `@Scheduled` `SlaMonitor`.
- (c) Reuse the offline event/listener abstraction for all triggers — conflates event-driven and time-driven triggers.

**Decision:** Chose (b). `SlaMonitor` is `@Scheduled(fixedRateString = "${sla.check.interval-ms}")`. It queries `ASSIGNED` orders, computes minutes remaining against `slaDeadline`, and calls `suggestForOrder()` for at-risk orders with `TriggerReason.SLA_AT_RISK`. The idempotency guard is the same repository method used for `AGENT_OFFLINE`, just called with a different `TriggerReason`.

**Tradeoff:** A poll is less immediate than an event; an order may sit up to `sla.check.interval-ms` past the threshold before being noticed. For SLAs measured in tens of minutes, a 30-second polling granularity is acceptable for a hackathon build.

### Decision 7 — How does SSE streaming fit when the routing contract is synchronous?

**Context:** The brief requested `POST /orders/{id}/suggest/stream` to stream LLM reasoning token-by-token, but `RoutingStrategy.recommend()` returns a synchronous `List<AgentRecommendation>`.

**Options considered:**
- (a) Make `RoutingStrategy` return a stream/callback type — forces every strategy (including rule-based, which has nothing to stream) into a streaming API and likely requires WebFlux.
- (b) Keep `RoutingStrategy` synchronous and implement streaming as a parallel AI-specific path in `ReassignmentService` that reuses the same eligibility filter and `persist()` helper.
- (c) Separate streaming service/controller — avoids touching `ReassignmentService` but duplicates eligibility and persistence logic.

**Decision:** Chose (b). `ReassignmentService.streamSuggestion()` calls `PromptBuilder` and `LLMGateway.streamLLM()` directly, pushes `token` SSE events, parses/validates with the shared `LLMResponseParser`, persists via `persist()`, and falls back to rule-based if streaming fails.

**Tradeoff:** `ReassignmentService` now has AI-specific dependencies for one bonus method, a small boundary violation versus ADR-1. A production streaming feature would deserve a dedicated `StreamingSuggestionService`.

### Decision 8 — How is agent load kept accurate under concurrent suggestions?

**Context:** Originally `Agent.activeOrderCount` was only set by seed data and never updated. When one agent went offline with multiple orders, every suggestion call saw the same counts and recommended the same lowest-load agent (Rahul Verma for every stranded order). Sprint 2 also needed counts for capacity filtering.

**Fix applied:**
1. `OrderController.createOrder()` increments the assigned agent’s count.
2. `ReassignmentService.persist()` reserves capacity on the recommended agent when a `PENDING` suggestion is created.
3. `ReassignmentService.resolveSuggestion()`:
   - **ACCEPTED:** decrements the previous agent’s load (the new agent was already reserved).
   - **REJECTED:** releases the reservation on the recommended agent.
4. `AgentRepository.findByStatusNot(OFFLINE)` is used instead of `findByStatus(AVAILABLE)` so `BUSY` agents with spare capacity remain eligible.
5. A guard prevents creating a second `PENDING` suggestion for the same order, avoiding double reservation.

This ensures a batch of stranded orders is distributed across eligible agents instead of piling onto one rider.

---

## Business Logic & Domain Rules

### Domain model

- **Agent:** a delivery rider/vehicle with `status`, `currentZone`, `maxCapacity`, `canHandleHeavy`, `activeOrderCount`.
- **Order:** a delivery job with `assignedAgentId`, `pickupZone`, `dropoffZone`, `weightClass`, `slaDeadline`, `status`.
- **ReassignmentSuggestion:** a proposed move from current agent to recommended agent, with `confidence`, `reasoning`, `status`, and `triggerReason`.

### Business flow

1. Agent becomes `OFFLINE`.
2. `AgentOfflineListener` (async) finds every order still assigned to that agent.
3. For each stranded order, `ReassignmentService.suggestForOrder()` is called.
4. `AgentEligibilityFilter` removes `OFFLINE`, over-capacity, and weight-incapable agents.
5. The active `RoutingStrategy` ranks the remaining agents.
6. A `PENDING` `ReassignmentSuggestion` is persisted and the recommended agent’s capacity is reserved.
7. Operator accepts or rejects:
   - **ACCEPTED:** order’s `assignedAgentId` updates, previous agent’s load drops, status becomes `REASSIGNED`.
   - **REJECTED:** reserved load is released, order returns to `ASSIGNED`.
8. `SlaMonitor` independently creates proactive suggestions for orders near SLA breach.

### Business rules enforced in code

1. **Online-only rule:** only agents whose status is not `OFFLINE` can be recommended.
2. **Capacity rule:** an agent is eligible only if `activeOrderCount < maxCapacity`.
3. **Heavy-item rule:** a `HEAVY` order can only go to an agent with `canHandleHeavy = true`.
4. **Load-balancing rule:** among eligible agents, the rule-based strategy picks the one with the lowest `activeOrderCount`.
5. **Zone-preference rule:** the zone-affinity strategy prefers agents already in the order’s `pickupZone`, breaking ties by load.
6. **One-pending-per-order rule:** a new suggestion cannot be created while a `PENDING` suggestion already exists for that order.
7. **Idempotency rule:** the same offline event will not create duplicate `AGENT_OFFLINE` suggestions for the same order.
8. **Reservation rule:** creating a `PENDING` suggestion increments the recommended agent’s `activeOrderCount`; rejecting it decrements it.
9. **SLA risk rule:** `ASSIGNED` orders within the configured risk threshold of their deadline are surfaced as proactive suggestions.

---

## Routing Strategies

| Strategy | Bean name | Decision logic | Best for |
|----------|-----------|----------------|----------|
| **Rule-based** | `rule` (default) | Sorts eligible agents by `activeOrderCount`, picks lowest | Fast, deterministic, zero external dependency |
| **AI** | `ai` | Groq LLM receives a structured prompt with order zones, weight, SLA context, and full agent roster; returns a JSON recommendation that is parsed and validated | Smart, context-aware recommendations |
| **Zone affinity** | `zone` | Prefers agents whose `currentZone` matches `pickupZone`; falls back to lowest load | Minimizing agent travel distance |

Switch at runtime:

```powershell
# PowerShell
$env:ROUTING_STRATEGY="ai"    # or "rule", "zone"
```

```bash
# Linux / macOS
export ROUTING_STRATEGY=ai
```

Adding a new strategy: implement `RoutingStrategy` and annotate the class `@Component("newname")`; the resolver, service, controllers, and listeners do not change.

---

## LLM Integration

The engine calls Groq’s OpenAI-compatible chat endpoint.

```properties
llm.provider=groq
llm.api-key=${LLM_API_KEY:}
llm.model=openai/gpt-oss-20b
llm.base-url=https://api.groq.com
llm.timeout-ms=8000
```

- **`llm.model`**: `openai/gpt-oss-20b` — fast, JSON-mode friendly, available on the free Groq tier. Groq’s model list is account-specific; query `GET https://api.groq.com/openai/v1/models` with your key and update the property if it 404s.
- **`llm.timeout-ms`**: hard cap of 8 seconds so a slow LLM cannot block the reassignment executor.
- **Fallback**: if the LLM fails, returns malformed JSON, or recommends an invalid/incapable agent, `ReassignmentService` catches the failure and silently falls back to the rule-based strategy.

---

## Database Schema

| Table | Purpose | Key columns |
|-------|---------|-------------|
| `agents` | Delivery riders/vehicles | `id`, `name`, `status`, `active_order_count`, `current_zone`, `max_capacity`, `can_handle_heavy` |
| `orders` | Delivery jobs | `id`, `description`, `assigned_agent_id`, `status`, `sla_deadline`, `pickup_zone`, `dropoff_zone`, `weight_class` |
| `reassignment_suggestions` | Proposed reassignments awaiting approval | `id`, `order_id`, `recommended_agent_id`, `confidence`, `reasoning`, `status`, `trigger_reason` |

### How the tables interact

- `agents.active_order_count` is incremented when an order is created or when a `PENDING` suggestion reserves capacity; it is decremented when a suggestion is accepted (old agent) or rejected (new agent).
- `orders.status` moves `ASSIGNED` → `REASSIGNMENT_PENDING` → `REASSIGNED` or back to `ASSIGNED`.
- `reassignment_suggestions` links an `order_id` to a `recommended_agent_id` and records the `trigger_reason` (`INITIAL`, `AGENT_OFFLINE`, `SLA_AT_RISK`).

---

## API Reference

### Orders
| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/orders` | Create a new pre-assigned order |
| `GET` | `/orders` | List all orders (optional `?status=` filter) |
| `POST` | `/orders/{id}/suggest` | Run the active routing strategy and create a suggestion |
| `POST` | `/orders/{id}/suggest/stream` | Stream the AI’s reasoning live via SSE |

### Agents
| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/agents` | List all agents |
| `PATCH` | `/agents/{id}/status` | Update agent status (`AVAILABLE`, `BUSY`, `OFFLINE`). `OFFLINE` triggers the agentic re-plan loop. |

### Suggestions
| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/suggestions` | List all suggestions |
| `PATCH` | `/suggestions/{id}` | Accept or reject a suggestion (`{ "status": "ACCEPTED" }` or `REJECTED`) |

### Health
| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/actuator/health` | Liveness / readiness check |
| `GET` | `/actuator/info` | Basic application metadata |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+
- Docker (for PostgreSQL)
- A Groq API key from [https://console.groq.com](https://console.groq.com)

### Step 1 — Start PostgreSQL

```bash
docker run -d --name ziprun-postgres \
  -e POSTGRES_USER=ziprun \
  -e POSTGRES_PASSWORD=ziprun123 \
  -e POSTGRES_DB=ziprun_db \
  -p 5434:5432 postgres:15-alpine
```

### Step 2 — Set the Groq API key

```powershell
# PowerShell
$env:LLM_API_KEY="your-groq-api-key-here"
```

```bash
# Linux / macOS
export LLM_API_KEY="your-groq-api-key-here"
```

### Step 3 — Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs on **http://localhost:8080**.

### Step 4 — Run the frontend

```bash
cd frontend
npm install
npm start
```

Frontend runs on **http://localhost:4200**.

---

## Configuration & Environment Variables

| Variable | Default | Effect |
|----------|---------|--------|
| `LLM_API_KEY` | empty (required for AI strategy) | Groq API key |
| `ROUTING_STRATEGY` | `rule` | Active routing strategy: `rule`, `ai`, or `zone` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5434/ziprun_db` | PostgreSQL connection |

### `application.properties` explained

```properties
llm.provider=groq
llm.api-key=${LLM_API_KEY:}
llm.model=openai/gpt-oss-20b
llm.base-url=https://api.groq.com
llm.timeout-ms=8000
```

- `llm.provider` / `llm.base-url`: Groq endpoint.
- `llm.api-key`: injected from env; no secret in source code.
- `llm.model`: configurable because Groq’s available models change by account.
- `llm.timeout-ms`: prevents a slow LLM from stalling the reassignment executor.

```properties
management.endpoints.web.exposure.include=health,info
```

- Exposes `/actuator/health` and `/actuator/info` for monitoring / Kubernetes probes.

```properties
sla.check.interval-ms=30000
sla.risk.threshold-minutes=10
```

- `sla.check.interval-ms`: `SlaMonitor` runs every 30 seconds.
- `sla.risk.threshold-minutes`: `ASSIGNED` orders within 10 minutes of deadline (or already past) get a proactive suggestion.

```properties
routing.strategy=${ROUTING_STRATEGY:rule}
```

- Picks the active `RoutingStrategy` bean by name. Override with `ROUTING_STRATEGY` env var; no restart needed.

---

## Demo Flow

> For a recorded walkthrough, see the **[Demo Video](https://drive.google.com/file/d/1NpqjxEEKqaMED3gzhRpJ7PrA3-ueYI-s/view?usp=drive_link)**.

1. Open **http://localhost:4200**.
2. The **Ops Board** shows agents, pending suggestions, and orders.
3. Click **Flip Offline** on an agent with active orders.
4. Within seconds, suggestions appear for every stranded order, each typically recommending a different agent because capacity is reserved on the first pick.
5. Click **Accept** → order becomes `REASSIGNED`, load bars update.
6. Click **Reject** → order returns to `ASSIGNED`, the recommended agent’s load drops.
7. Flip the same agent offline again → no duplicate suggestions appear.
8. Open the **Full Dispatch Board** tab to see SLA countdowns (green / amber / red) and zone rosters.
9. Click **Stream AI Suggestion** to watch the LLM reasoning arrive token-by-token.

---

## Why This Product is Strong

| Strength | Evidence |
|----------|----------|
| **Pluggable strategies** | `RoutingStrategy` interface + Spring bean map; new strategy = one class + one annotation |
| **No-restart strategy switching** | `routing.strategy` read at call time |
| **Resilient AI** | AI failures throw, `ReassignmentService` falls back to rule-based |
| **Async + idempotent re-plan** | `@Async` listener + `existsByOrderIdAndTriggerReasonAndStatus` guard |
| **Capacity reservation** | `PENDING` suggestions reserve `activeOrderCount`; rejected suggestions release it |
| **Proactive SLA loop** | `SlaMonitor` reuses the same `suggestForOrder()` pipeline |
| **Explainability** | Every suggestion returns `confidence` and human-readable `reasoning` |
| **Live streaming** | SSE endpoint streams LLM tokens as they arrive |
| **Clean separation** | `RoutingStrategy` decides, `ReassignmentService` orchestrates, controllers/events trigger |

---

## Future Roadmap

- **Delivered order cleanup:** a `DELIVERED` status can decrement `activeOrderCount` and close the order.
- **Retryable AI failures:** distinguish timeout (retryable) from hallucinated id (non-retryable).
- **Dead-letter queue:** listener-level failures pushed to a retry topic or alert.
- **Multi-region expansion:** the `current_zone` seam already supports zone-aware scaling.

---

## Branch Note

All features described above — pluggable routing, AI fallback, SLA monitor, SSE streaming, capacity reservation, and expanded seed data — are on the `sprint-2-extensions` branch. The `main` branch remains the original submitted assessment snapshot and is intentionally left untouched.
