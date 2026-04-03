# Roundtable — Multi-Agent AI Financial Intelligence Platform

> Pose a financial question. Four specialist AI agents — each powered by a different
> free-tier model — debate it with real market data, round by round. A Synthesiser
> distils the debate into a structured decision brief.

---

## What It Does

Roundtable is a locally-run Java application that runs structured debates between
specialist AI agents to help examine financial decisions from multiple angles
before acting.

**Default agents:**

| Agent | Domain | Model | Provider |
|---|---|---|---|
| The Fundamentalist | Valuation, earnings, financials | Gemini 2.0 Flash | Google AI Studio |
| The Risk Officer | Downside, volatility, drawdown | Llama 3.3 70B | Groq |
| The Macro Strategist | Rates, inflation, geopolitics | Mistral Small | Mistral AI |
| The Contrarian | Challenges consensus | Llama 3.3 (free) | OpenRouter |
| The Synthesiser | Post-debate brief | Gemini 2.0 Flash | Google AI Studio |

All agents are configurable in the database — no code changes needed to add, edit,
or deactivate any agent.

---

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.2, Maven
- **Database:** PostgreSQL 16 + pgvector extension
- **Migrations:** Flyway
- **Frontend:** Vanilla HTML / CSS / JS (no build step)
- **Containerisation:** Docker + Docker Compose
- **AI Providers:** Google AI Studio, Groq, Mistral, OpenRouter (all free tier)

---

## Prerequisites

- Java 17+ — check with `java -version`
- Maven 3.8+ — check with `mvn -version`
- Docker + Docker Compose — for PostgreSQL
- Five free API keys (takes ~10 minutes):

| Key | Where to get it |
|---|---|
| Gemini | https://aistudio.google.com/app/apikey |
| Groq | https://console.groq.com/keys |
| Mistral | https://console.mistral.ai/api-keys |
| OpenRouter | https://openrouter.ai/settings/keys |
| FRED (macro data) | https://fred.stlouisfed.org/docs/api/api_key.html |

---

## Setup

```bash
# 1. Clone
git clone https://github.com/YOUR_USERNAME/roundtable.git
cd roundtable

# 2. Create your local config (gitignored — keys never leave your machine)
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties

# 3. Open application-local.properties and fill in your 5 API keys

# 4. Start PostgreSQL
docker-compose up -d db

# 5. Run the app (Flyway runs migrations automatically on startup)
mvn spring-boot:run

# 6. Open
open http://localhost:8080
```

Or run everything in Docker:
```bash
docker-compose up -d
```

---

## Project Structure

```
src/main/java/com/roundtable/
├── RoundtableApplication.java        Entry point
├── api/                              REST controllers
│   ├── DebateController.java         POST /api/debate/start, /continue
│   ├── SessionController.java        GET/POST /api/sessions
│   ├── ConfigController.java         GET /api/config/*
│   └── dto/SessionRequest.java       Inbound request DTO
├── agent/                            Agent layer
│   ├── AgentRegistry.java            Loads agents from DB
│   ├── AgentDefinition.java          Agent value object
│   └── provider/                     One class per AI provider
│       ├── AIProviderService.java    Interface
│       ├── GeminiProvider.java
│       ├── GroqProvider.java
│       ├── MistralProvider.java
│       └── OpenRouterProvider.java
├── config/                           Spring configuration
├── data/                             Data module
│   ├── DataService.java              Interface
│   ├── DataServiceImpl.java          Cache + fetch orchestration
│   ├── DataSourceAdapter.java        Interface for data sources
│   ├── cache/DataCacheService.java   PostgreSQL-backed cache
│   └── source/                       One class per data source
│       ├── YahooFinanceAdapter.java  Prices, fundamentals, earnings
│       └── FredApiAdapter.java       Macro indicators
├── memory/                           Memory module
│   ├── MemoryService.java            Interface
│   ├── MemoryServiceImpl.java        Session CRUD
│   ├── ContextLoader.java            Loads prior rounds for context
│   ├── MemoryRecords.java            Domain value objects
│   └── SynthesisService.java         Gemini post-debate synthesis
├── module/                           Analysis modules
│   ├── AnalysisModule.java           Interface
│   ├── ModuleInput.java
│   ├── ModuleOutput.java
│   └── debate/
│       ├── DebateModule.java         Module entry point
│       ├── DebateOrchestrator.java   Sequential agent execution
│       └── OutputValidator.java     Format enforcement + retry
├── mcp/McpToolRegistry.java          MCP stub (wired in Phase 2)
├── logging/                          Structured event logging
│   ├── EventLogger.java              Interface
│   └── EventLoggerImpl.java          DB + SLF4J implementation
└── exception/                        Error handling
    ├── ProviderException.java
    ├── ModuleException.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.properties                   Main config (committed)
├── application-local.properties.example     Key template (committed)
├── application-local.properties             Your keys (gitignored)
├── db/migration/
│   ├── V1__initial_schema.sql               Full schema
│   └── V2__seed_defaults.sql                Default agents + prompts
└── static/                                  Frontend
    ├── index.html
    ├── css/styles.css
    └── js/app.js
```

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/debate/start` | Start a new debate session |
| `POST` | `/api/debate/continue/{id}` | Continue an existing session |
| `GET` | `/api/sessions` | List active ongoing sessions |
| `GET` | `/api/sessions/{id}` | Load a specific session |
| `POST` | `/api/sessions/combine/prepare` | Prepare combined context |
| `POST` | `/api/sessions/{id}/conclude` | Mark session as concluded |
| `GET` | `/api/config/providers` | Provider status (no keys exposed) |
| `GET` | `/api/config/agents` | Active agent list |
| `GET` | `/api/config/settings` | Debate settings |

---

## Running Tests

```bash
mvn test
```

---

## Building a Production JAR

```bash
mvn clean package -DskipTests
java -jar target/roundtable-2.0.0.jar
```

Pass keys as environment variables in production:
```bash
GEMINI_API_KEY=xxx GROQ_API_KEY=xxx ... java -jar target/roundtable-2.0.0.jar
```

---

## Extending the Platform

**Add a new AI provider:**
1. Implement `AIProviderService` (or extend `OpenAICompatibleProvider`)
2. Add config block in `application.properties`
3. Done — it auto-registers as a Spring bean

**Add a new data source:**
1. Implement `DataSourceAdapter`
2. Register as `@Service`
3. Done — `DataServiceImpl` picks it up automatically

**Add a new analysis module:**
1. Follow the 6-step process in the PRD (Section 2.3)
2. Implement `AnalysisModule`
3. Add a row to the `modules` table via Flyway migration

---

## Disclaimer

> AI-generated analysis for informational purposes only.
> Not financial advice. Always verify information independently
> before making any investment decisions.

---

**Version:** 2.0.0 — Phase 1: Hardened Debate Core
