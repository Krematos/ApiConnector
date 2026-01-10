CREATE TABLE IF NOT EXISTS transaction_audit (
    id SERIAL PRIMARY KEY,
    internal_order_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    service_type VARCHAR(50),
    status VARCHAR(50),
    details TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    notification_sent BOOLEAN DEFAULT FALSE
);

-- Tabulka pro ShedLock
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
