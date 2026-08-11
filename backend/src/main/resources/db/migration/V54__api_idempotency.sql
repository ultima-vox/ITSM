CREATE TABLE api_idempotency_record (
    org_id           varchar(120) NOT NULL,
    actor_id         varchar(255) NOT NULL,
    operation_key    varchar(160) NOT NULL,
    idempotency_key  varchar(128) NOT NULL,
    request_hash     char(64) NOT NULL,
    response_json    jsonb,
    created_at       timestamptz NOT NULL,
    completed_at     timestamptz,
    expires_at       timestamptz NOT NULL,
    PRIMARY KEY (org_id, actor_id, operation_key, idempotency_key),
    CONSTRAINT api_idempotency_completion_ck CHECK (
        (response_json IS NULL AND completed_at IS NULL)
        OR (response_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX api_idempotency_expiry_idx ON api_idempotency_record (expires_at);

