-- ===========================================================================
-- Express Corporation of India - schema
-- Idempotent: safe to run on every startup in dev (spring.sql.init.mode=always)
-- ===========================================================================

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(120)  NOT NULL,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    phone           VARCHAR(20)   NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    role            VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',   -- CUSTOMER | ADMIN
    enabled         TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS branches (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_name     VARCHAR(120) NOT NULL,
    city            VARCHAR(80)  NOT NULL,
    state           VARCHAR(80)  NOT NULL,
    pincode         VARCHAR(10)  NOT NULL,
    phone           VARCHAR(20),
    address         VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shipments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_id         VARCHAR(20)   NOT NULL UNIQUE,
    sender_name         VARCHAR(120)  NOT NULL,
    sender_phone        VARCHAR(20)   NOT NULL,
    sender_address      VARCHAR(255)  NOT NULL,
    receiver_name       VARCHAR(120)  NOT NULL,
    receiver_phone      VARCHAR(20)   NOT NULL,
    receiver_address    VARCHAR(255)  NOT NULL,
    origin_city         VARCHAR(80)   NOT NULL,
    destination_city    VARCHAR(80)   NOT NULL,
    weight_kg           DECIMAL(6,2)  NOT NULL DEFAULT 0.5,
    service_type        VARCHAR(30)   NOT NULL DEFAULT 'DOMESTIC_STANDARD', -- DOMESTIC_STANDARD | DOMESTIC_EXPRESS | INTERNATIONAL
    status              VARCHAR(30)   NOT NULL DEFAULT 'BOOKED',            -- BOOKED | PICKED_UP | IN_TRANSIT | AT_HUB | OUT_FOR_DELIVERY | DELIVERED | CANCELLED | RTO
    booked_by_user_id   BIGINT,
    expected_delivery    DATE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipment_user FOREIGN KEY (booked_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    KEY idx_shipments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracking_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT        NOT NULL,
    status          VARCHAR(30)   NOT NULL,
    location        VARCHAR(120)  NOT NULL,
    remarks         VARCHAR(255),
    event_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE,
    KEY idx_events_shipment (shipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS contact_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    subject         VARCHAR(150),
    message         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
