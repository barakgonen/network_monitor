CREATE TABLE IF NOT EXISTS messages (
    id                  VARCHAR(64)   PRIMARY KEY,
    observed_at         TIMESTAMP     NOT NULL,
    transport_protocol  VARCHAR(16)   NOT NULL,
    remote_address      VARCHAR(64)   NOT NULL,
    local_port          INT           NOT NULL,
    interface_name      VARCHAR(128)  NOT NULL,
    message_type        VARCHAR(128)  NOT NULL,
    header_json         CLOB,
    body_json           CLOB,
    payload_size_bytes  INT           NOT NULL,
    payload_text        CLOB,
    payload_base64      CLOB,
    parse_error         VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_messages_observed_at ON messages (observed_at);
CREATE INDEX IF NOT EXISTS idx_messages_interface_name ON messages (interface_name);
CREATE INDEX IF NOT EXISTS idx_messages_message_type ON messages (message_type);
