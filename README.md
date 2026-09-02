# ZipRun AI Reassignment Engine

> **Real-time, AI-assisted delivery dispatch engine that keeps orders moving when agents go offline.**
>
> Built as a production-ready Spring Boot backend + Angular dashboard with pluggable routing strategies, LLM-powered recommendations, proactive SLA monitoring, and live reasoning streaming.

---

## 1. What problem does this product solve? (Business value)

In last-mile logistics, a delivery agent can suddenly become unavailable — bike breakdown, traffic accident, illness, or network loss. The orders they are carrying are now **stranded**. Every minute of delay:
- damages customer trust,
- incurs SLA penalties,
- forces the operations team to manually scan maps and reassign loads.

**ZipRun removes that manual fire-fighting.** It detects the offline event, evaluates every stranded order, recommends the best replacement agent with explainable AI, and queues those recommendations for a single operator approval. It also **predicts SLA risk before a breach happens**, so operations can act early.

### Why this is useful for the business
| Pain point | How ZipRun solves it |
|------------|----------------------|
| Manual reassignment is slow and error-prone | Async agentic loop finds and proposes replacements in seconds |
| One agent going offline stalls multiple orders | All stranded orders are re-planned as a batch |
| Overloading a single replacement agent | Load + capacity rules prevent over-assignment |
| Heavy / bulky items given to wrong vehicle | Weight-class eligibility enforces vehicle capability |
| SLA breaches discovered too late | Proactive SLA monitor creates suggestions before the deadline |
| Black-box AI recommendations | Every suggestion includes `confidence` and `reasoning` |
| LLM outages break the flow | Rule-based fallback is always ready |

---

## 2. What does the product stand for?

**ZipRun** stands for:
- **Resilience:** the system degrades gracefully — AI failures fall back to deterministic rules.
- **Explainability:** every routing decision is auditable.
- **Extensibility:** new routing strategies plug in with zero changes to existing code.
- **Proactivity:** it acts on SLA risk before it becomes an SLA breach.

In short: *an intelligent, human-in-the-loop reassignment copilot for delivery operations.*

---

## 3. Core business logic

### 3.1 Domain model

- **Agent:** a delivery rider/vehicle with `status`, `currentZone`, `maxCapacity`, `canHandleHeavy`, and `activeOrderCount`.
- **Order:** a delivery job with `assignedAgentId`, `pickupZone`, `dropoffZone`, `weightClass`, `slaDeadline`, and `status`.
- **ReassignmentSuggestion:** a proposed move of an order from its current agent to a recommended agent.

### 3.2 Business flow

1. An agent becomes `OFFLINE` (operator clicks “Flip Offline” or an external event posts `PATCH /agents/{id}/status`).
2. `AgentOfflineListener` (async) finds every order still assigned to that agent.
3. For each stranded order it calls `ReassignmentService.suggestForOrder(...)`.
4. `AgentEligibilityFilter` removes agents that are **OFFLINE**, **over capacity**, or **cannot carry the weight class**.
5. The active `RoutingStrategy` ranks the remaining agents and returns a recommendation.
6. A `PENDING` `ReassignmentSuggestion` is saved; the recommended agent’s capacity is **reserved** immediately so the next stranded order does not pile onto the same agent.
7. The operator accepts or rejects the suggestion:
   - **ACCEPTED:** the order’s `assignedAgentId` is updated, previous agent’s load drops, order status becomes `REASSIGNED`.
   - **REJECTED:** the reserved capacity on the recommended agent is released, order returns to `ASSIGNED`.
8. `SlaMonitor` (scheduled) independently scans `ASSIGNED` orders and creates proactive suggestions when an SLA deadline is within 10 minutes or already past.

### 3.3 Business rules written in code

1. **Online-only rule:** Only agents whose status is not `OFFLINE` can be recommended.
2. **Capacity rule:** An agent is eligible only if `activeOrderCount < maxCapacity`.
3. **Heavy-item rule:** A `HEAVY` order can only be assigned to an agent with `canHandleHeavy = true`.
4. **Load-balancing rule (rule-based strategy):** Among all eligible agents, recommend the one with the lowest `activeOrderCount`.
5. **One-pending-per-order rule:** A new suggestion cannot be created while another `PENDING` suggestion already exists for the same order.
6. **Idempotency rule:** The same offline event will not create duplicate `AGENT_OFFLINE` suggestions for the same order.
7. **Reservation rule:** Creating a `PENDING` suggestion immediately increments the recommended agent’s `activeOrderCount`; rejecting it decrements it again.
8. **SLA risk rule:** Orders within the configured risk threshold of their SLA deadline are automatically surfaced as proactive suggestions.

---

## 4. Routing strategies applied

The engine is built around a `RoutingStrategy` interface. Switching strategies is a one-line environment change, **no restart required**.

| Strategy | Key name | When to use | How it decides |
|----------|----------|-------------|----------------|
| **Rule-based** | `rule` (default) | Fast, deterministic, zero external dependency | Eligible agents sorted by `activeOrderCount`; lowest load wins. |
| **AI** | `ai` | Rich, context-aware recommendations | Groq LLM receives a prompt with order zones, weight, SLA context, and full agent roster; it returns a JSON recommendation that is parsed and validated. |
| **Zone affinity** | `zone` | Minimize agent travel distance | Prefers agents whose `currentZone` matches the order’s `pickupZone`; if no zone match, falls back to lowest load. |

### Switching strategy

```powershell
# PowerShell
$env:ROUTING_STRATEGY="ai"     # or "rule", "zone"
```

```bash
# Linux / macOS
export ROUTING_STRATEGY=ai
```

Adding a new strategy requires only one class implementing `RoutingStrategy` and annotated `@Component("yourname")`; the existing resolver, service, controllers, and listeners do not change.

---

## 5. Tech stack

### Backend
- **Java 21** with **Spring Boot 3.2**
- **Spring Data JPA** + **PostgreSQL**
- **Spring `@Async`** for offline event handling
- **Spring `@Scheduled`** for proactive SLA monitoring
- **Spring Boot Actuator** for health/info endpoints
- **RestClient** for Groq LLM calls
- **SSE (Server-Sent Events)** for live AI reasoning streaming
- **Maven** for builds

### Frontend
- **Angular 17** standalone architecture
- **RxJS** for polling the ops board
- **Fetch + ReadableStream** for SSE token streaming

### Infrastructure
- **PostgreSQL** via Docker
- **Groq API** for LLM (free tier works)

---

## 6. LLM model details

The engine calls Groq’s OpenAI-compatible chat endpoint.

```properties
llm.provider=groq
llm.api-key=${LLM_API_KEY:}
llm.model=openai/gpt-oss-20b
llm.base-url=https://api.groq.com
llm.timeout-ms=8000
```

- **`llm.model`**: `openai/gpt-oss-20b` — chosen because it is fast, JSON-mode friendly, and available on the free Groq tier. Groq’s model list changes per account; if this model is unavailable, query `GET https://api.groq.com/openai/v1/models` with your key and update the property.
- **`llm.timeout-ms`**: hard cap of 8 seconds so a slow LLM cannot block the reassignment executor.
- **Fallback**: if the LLM fails, returns malformed JSON, or recommends an agent that does not exist / is ineligible, `ReassignmentService` catches the failure and silently falls back to the rule-based strategy.

---

## 7. Database tables and their purpose

| Table | Purpose | Key columns |
|-------|---------|-------------|
| `agents` | Delivery riders/vehicles | `id`, `name`, `status`, `active_order_count`, `current_zone`, `max_capacity`, `can_handle_heavy` |
| `orders` | Delivery jobs | `id`, `description`, `assigned_agent_id`, `status`, `sla_deadline`, `pickup_zone`, `dropoff_zone`, `weight_class` |
| `reassignment_suggestions` | Proposed reassignments awaiting operator approval | `id`, `order_id`, `recommended_agent_id`, `confidence`, `reasoning`, `status`, `trigger_reason` |

### How the tables interact
- `agents.active_order_count` is incremented when an order is created or when a `PENDING` suggestion reserves capacity; it is decremented when a suggestion is accepted (old agent) or rejected (new agent).
- `orders.status` moves `ASSIGNED` → `REASSIGNMENT_PENDING` → `REASSIGNED` or back to `ASSIGNED`.
- `reassignment_suggestions` links an `order_id` to a `recommended_agent_id` and records `trigger_reason` (`INITIAL`, `AGENT_OFFLINE`, `SLA_AT_RISK`).

---

## 8. API endpoints

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

## 9. How to run the project

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

Backend runs on **http://localhost:8080**. Seed data loads automatically.

### Step 4 — Run the frontend

```bash
cd frontend
npm install
npm start
```

Frontend runs on **http://localhost:4200**.

### Optional environment variables

| Variable | Default | Effect |
|----------|---------|--------|
| `LLM_API_KEY` | empty (required for AI strategy) | Groq API key |
| `ROUTING_STRATEGY` | `rule` | Active routing strategy: `rule`, `ai`, or `zone` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5434/ziprun_db` | PostgreSQL connection |

---

## 10. Demo flow

1. Open **http://localhost:4200**.
2. The **Ops Board** shows agents, pending suggestions, and orders.
3. Click **Flip Offline** on an agent with active orders.
4. Within seconds, suggestions appear for every stranded order, each with a different recommended agent (load reservation prevents piling onto one rider).
5. Click **Accept** → order becomes `REASSIGNED`, load bars update.
6. Click **Reject** → order returns to `ASSIGNED`, the recommended agent’s load drops.
7. Flip the same agent offline again → no duplicate suggestions appear (idempotency).
8. Open the **Full Dispatch Board** tab to see SLA countdowns (green / amber / red), agent load bars, and zone rosters.
9. Click **Stream AI Suggestion** to watch the LLM reasoning arrive token-by-token.

---

## 11. Why this product is technically strong

| Strength | Evidence in the code |
|----------|----------------------|
| **Pluggable strategies** | `RoutingStrategy` interface + Spring bean map; add a strategy with one class and one annotation |
| **No-restart strategy switching** | `routing.strategy` is read at call time, not at startup |
| **Resilient AI** | `AiRoutingStrategy` throws on any failure; `ReassignmentService` falls back to rule-based |
| **Async + idempotent re-plan** | `@Async` listener + `existsByOrderIdAndTriggerReasonAndStatus` guard |
| **Capacity reservation** | `PENDING` suggestions reserve `activeOrderCount`; rejected suggestions release it |
| **Proactive SLA loop** | `SlaMonitor` scans `ASSIGNED` orders and creates suggestions before breach |
| **Explainability** | Every suggestion returns `confidence` and human-readable `reasoning` |
| **Live streaming** | `POST /orders/{id}/suggest/stream` pushes SSE tokens as the LLM generates them |
| **Clean separation** | `RoutingStrategy` for decisions, `ReassignmentService` for orchestration, controllers/events for triggers |

---

## 12. Future extensions (already designed in)

- **New routing strategies:** implement `RoutingStrategy` and annotate `@Component("newname")`; no other file changes.
- **Delivered order cleanup:** a `DELIVERED` status can decrement `activeOrderCount` and close the order.
- **Multi-region deployment:** the `current_zone` seam supports zone-aware expansion.
- **Dead-letter / retry queue:** listener failures are logged; a production version would push them to a retry topic.

---

## 13. Branch note

All features described above — pluggable routing, AI fallback, SLA monitor, SSE streaming, capacity reservation, and expanded seed data — are on the `sprint-2-extensions` branch. The `main` branch contains the original submitted assessment snapshot and is intentionally left untouched.
