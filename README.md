# ZipRun — AI Reassignment Engine

A reactive reassignment engine: when a delivery agent goes offline, the system automatically detects affected orders, uses AI (with a rule-based fallback) to recommend reassignments, and queues them for ops approval.

## Stack

- **Backend:** Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL
- **Frontend:** Angular 17
- **AI:** Groq (Llama 3.1) via a lightweight LLM gateway

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

1. Open http://localhost:4200 — see the agent roster and order list.
2. Flip an agent with active orders to `OFFLINE`.
3. Watch reassignment suggestions appear automatically (poll/refresh) with an "Auto re-plan" badge, AI reasoning, and confidence score.
4. Accept or reject the suggestion.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST | `/orders` | Create a pre-assigned order |
| GET | `/orders?status=` | List orders, filterable by status |
| PATCH | `/agents/{id}/status` | Update agent availability (triggers agentic loop on OFFLINE) |
| POST | `/orders/{id}/suggest` | Run active routing strategy on demand |
| PATCH | `/suggestions/{id}` | Accept or reject a suggestion |

## Architecture

See [ADR.md](./ADR.md) for the reasoning behind key design decisions.
