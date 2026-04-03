-- ─────────────────────────────────────────────────────────────────────────────
-- V1__initial_schema.sql
-- Roundtable Phase 1 — Full initial schema
--
-- All schema changes go through Flyway migrations.
-- Never modify the database manually.
-- ─────────────────────────────────────────────────────────────────────────────

-- Enable pgvector extension (required for RAG in Phase 2 — registered now,
-- so the column type is available without a future breaking migration)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ─── Module Registry ──────────────────────────────────────────────────────────
-- Tracks which analysis modules are active.
-- New modules = new row. No code change needed in the registry itself.
CREATE TABLE modules (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    version    VARCHAR(20)  NOT NULL DEFAULT '1.0',
    active     BOOLEAN      NOT NULL DEFAULT true,
    config     JSONB,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Agent Registry ───────────────────────────────────────────────────────────
-- All agents defined here. Never hardcoded in application code.
CREATE TABLE agents (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name          VARCHAR(100) NOT NULL,
    provider      VARCHAR(50)  NOT NULL,
    model         VARCHAR(100),
    domain        VARCHAR(200),
    persona       TEXT         NOT NULL,
    output_format TEXT,
    active        BOOLEAN      NOT NULL DEFAULT true,
    modules       TEXT[]       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Prompt Version Registry ─────────────────────────────────────────────────
-- All system prompts versioned here — never hardcoded in Java.
-- Every agent response records which prompt version was used.
CREATE TABLE prompt_versions (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id      UUID         REFERENCES agents(id) ON DELETE CASCADE,
    version       VARCHAR(20)  NOT NULL,
    system_prompt TEXT         NOT NULL,
    change_note   TEXT,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (agent_id, version)
);

-- ─── Sessions ─────────────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    module_id           UUID         REFERENCES modules(id),
    title               VARCHAR(300) NOT NULL,
    type                VARCHAR(20)  NOT NULL CHECK (type IN ('standalone', 'ongoing', 'combined')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'active'
                            CHECK (status IN ('active', 'paused', 'concluded')),
    global_context      TEXT,
    risk_tolerance      VARCHAR(20)  CHECK (risk_tolerance IN ('conservative', 'moderate', 'aggressive')),
    asset_class         VARCHAR(50),
    topic               TEXT         NOT NULL,
    parent_session_ids  UUID[]       NOT NULL DEFAULT '{}',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_active_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    concluded_at        TIMESTAMP
);

CREATE INDEX idx_sessions_status        ON sessions(status);
CREATE INDEX idx_sessions_type          ON sessions(type);
CREATE INDEX idx_sessions_last_active   ON sessions(last_active_at DESC);

-- ─── Debate Rounds ────────────────────────────────────────────────────────────
CREATE TABLE debate_rounds (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id   UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number INT          NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, round_number)
);

CREATE INDEX idx_debate_rounds_session ON debate_rounds(session_id);

-- ─── Agent Responses ──────────────────────────────────────────────────────────
CREATE TABLE agent_responses (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    round_id             UUID         NOT NULL REFERENCES debate_rounds(id) ON DELETE CASCADE,
    agent_id             UUID         REFERENCES agents(id),
    provider             VARCHAR(50)  NOT NULL,
    model                VARCHAR(100),
    structured_output    JSONB,          -- position, reasoning, key_risk, confidence
    raw_text             TEXT,
    prompt_version       VARCHAR(20),    -- which prompt version was active
    response_time_ms     INT,
    token_count          INT,
    success              BOOLEAN      NOT NULL DEFAULT true,
    error_message        TEXT,
    user_rating          SMALLINT     CHECK (user_rating BETWEEN 1 AND 5),
    flagged_hallucination BOOLEAN     NOT NULL DEFAULT false,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_responses_round   ON agent_responses(round_id);
CREATE INDEX idx_agent_responses_agent   ON agent_responses(agent_id);
CREATE INDEX idx_agent_responses_rating  ON agent_responses(user_rating);

-- ─── Session Synthesis ────────────────────────────────────────────────────────
-- Gemini synthesiser output after each round / end of debate
CREATE TABLE session_synthesis (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID  NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number   INT,              -- null = final synthesis
    consensus      JSONB,
    disputed       JSONB,
    strongest_case TEXT,
    key_risks      JSONB,
    suggested_next TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_synthesis_session ON session_synthesis(session_id);

-- ─── Session Outcomes (learning layer) ───────────────────────────────────────
-- Record what you actually decided and what happened.
-- This is how the system becomes useful for backtesting over time.
CREATE TABLE session_outcomes (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id     UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_decision  TEXT,
    outcome_notes  TEXT,
    outcome_date   DATE,
    quality_rating SMALLINT     CHECK (quality_rating BETWEEN 1 AND 5),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Data Cache ───────────────────────────────────────────────────────────────
-- Caches all external data fetches. TTLs defined in application.properties.
CREATE TABLE data_cache (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source     VARCHAR(100) NOT NULL,
    data_type  VARCHAR(100) NOT NULL,
    cache_key  VARCHAR(300) NOT NULL,   -- e.g. "NVDA:fundamentals"
    data       JSONB        NOT NULL,
    fetched_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_cache_key       ON data_cache(cache_key);
CREATE INDEX        idx_cache_expires   ON data_cache(expires_at);

-- ─── RAG Store ────────────────────────────────────────────────────────────────
-- pgvector column registered now so Phase 2 RAG is an addition, not a migration.
CREATE TABLE rag_documents (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type VARCHAR(100) NOT NULL,   -- data_fetch | news | agent_response | user_paste
    source_ref  TEXT,
    content     TEXT         NOT NULL,
    metadata    JSONB,
    embedding   vector(768),             -- Gemini text-embedding-004 dimension
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_source_type ON rag_documents(source_type);
-- Vector index added in Phase 2 migration when embeddings are populated

-- ─── Extensible Financial Entities ───────────────────────────────────────────
-- For Hidden Gem Finder and future modules.
-- core_data: stable financial fields as typed JSONB
-- extended_data: evolving fields — add new ones without migration
-- When extended fields mature, promote to columns via new Flyway migration.
CREATE TABLE financial_entities (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type   VARCHAR(50)  NOT NULL,   -- stock | fund | crypto | [future]
    ticker        VARCHAR(20),
    name          VARCHAR(300) NOT NULL,
    market        VARCHAR(100),
    sector        VARCHAR(100),
    country       VARCHAR(100),
    core_data     JSONB,
    extended_data JSONB,
    last_updated  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_entities_ticker ON financial_entities(ticker);
CREATE INDEX idx_entities_type   ON financial_entities(entity_type);

-- ─── Application Event Log ────────────────────────────────────────────────────
-- All ERROR and WARN events written here (queryable, session-linked).
-- INFO events written to file log only to keep table lean.
CREATE TABLE app_event_log (
    id          BIGSERIAL    PRIMARY KEY,
    event_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    level       VARCHAR(10)  NOT NULL CHECK (level IN ('ERROR', 'WARN', 'INFO')),
    module      VARCHAR(100),
    component   VARCHAR(200),
    event_type  VARCHAR(100),            -- PROVIDER_CALL | SESSION_START | DATA_FETCH | etc.
    session_id  UUID,
    agent_id    VARCHAR(100),
    message     TEXT         NOT NULL,
    metadata    JSONB,
    duration_ms INT
);

CREATE INDEX idx_event_log_time       ON app_event_log(event_time DESC);
CREATE INDEX idx_event_log_level      ON app_event_log(level);
CREATE INDEX idx_event_log_session    ON app_event_log(session_id);
CREATE INDEX idx_event_log_event_type ON app_event_log(event_type);
