CREATE TABLE click_events (
    event_id UUID PRIMARY KEY,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    occurred_at TIMESTAMPTZ NOT NULL,
    session_id VARCHAR(128) NOT NULL CHECK (length(session_id) > 0),
    page VARCHAR(2048) NOT NULL CHECK (length(page) > 0),
    element_id VARCHAR(128) NOT NULL CHECK (length(element_id) > 0)
);
