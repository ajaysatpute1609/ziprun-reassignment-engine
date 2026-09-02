# ZipRun — AI Reassignment Engine

A reactive reassignment engine: when a delivery agent goes offline, the system automatically detects affected orders, uses AI (with a rule-based fallback) to recommend reassignments, and queues them for ops approval.

## Stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL
- **Frontend:** Angular 17
- **AI:** Groq (`openai/gpt-oss-20b`, configurable) via a lightweight LLM gateway

## Prerequisites

- Java 17+, Maven
- Node.js 18+
- Docker (for Postgres)
- A Groq API key (https://console.groq.com) — free

## Setup (under 5 minutes)

### 1. Start Postgres

```bash
docker run -d --name ziprun-postgres -e POSTGRES_USER=ziprun -e POSTGRES_PASSWORD=ziprun123 -e POSTGRES_DB=ziprun_db -p 5434:5432 postgres:15-alpine
```

### 2. Set your Groq API key

```powershell
$env:LLM_API_KEY="your-groq-api-key-here"
```

The configured model is `openai/gpt-oss-20b` (fast, JSON-mode capable, available
on the free Groq tier at the time of writing). Groq's available model list is
account-specific and changes — if you get a `model_not_found` error, check
`GET https://api.groq.com/openai/v1/models` with your key and update
`llm.model` in `application.properties`.

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs on **http://localhost:8080**. Seed data (5 agents, 8 orders) loads automatically.

### 4. Run the frontend

```bash
cd frontend
npm install
npm start
```

Frontend runs on **http://localhost:4200**.

## Demo flow

1. Open http://localhost:4200 — see the agent roster and pending suggestions in **Ops View**.
2. Flip an agent with active orders to `OFFLINE` ("Flip Offline" button).
3. Watch reassignment suggestions appear automatically (poll/refresh, ~5s) with an "Auto re-plan" badge, AI reasoning, and confidence score.
4. Accept or reject the suggestion — accepted orders move to `REASSIGNED`.
5. Flip the same agent offline again — no duplicate suggestions appear (idempotency).
6. Switch to the **Full Dispatch Board** tab to see all orders across all statuses, SLA countdowns (green/amber/red), agent load bars, and zones.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST | `/orders` | Create a pre-assigned order |
| GET | `/orders?status=` | List orders, filterable by status |
| GET | `/agents` | List all agents |
| PATCH | `/agents/{id}/status` | Update agent availability (triggers agentic loop on OFFLINE) |
| POST | `/orders/{id}/suggest` | Run active routing strategy on demand |
| GET | `/suggestions` | List all suggestions |
| PATCH | `/suggestions/{id}` | Accept or reject a suggestion |
| GET | `/actuator/health` | Health check |

## Switching routing strategy at runtime

```powershell
$env:ROUTING_STRATEGY="ai"   # or "rule" — no restart needed, resolved per-call
```

## Verified end-to-end (manual test log)

- `POST /orders/{id}/suggest` → rule-based recommendation persisted.
- `PATCH /agents/AGT-005/status {OFFLINE}` → returns immediately; async listener
  (visible in logs on a `reassign-*` thread, not the request thread) creates
  `AGENT_OFFLINE` suggestions for all 3 stranded orders.
- Repeating the same PATCH → no duplicate suggestions (idempotency guard).
- `PATCH /suggestions/{id} {ACCEPTED}` → order flips to `REASSIGNED` with the
  recommended agent.

## Architecture

See [ADR.md](./ADR.md) for the reasoning behind key design decisions.
