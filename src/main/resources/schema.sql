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
    role            VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',   -- CUSTOMER | ADMIN | STAFF
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
    service_type        VARCHAR(30)   NOT NULL DEFAULT 'DOMESTIC_STANDARD',
    status              VARCHAR(30)   NOT NULL DEFAULT 'BOOKED',
    booked_by_user_id   BIGINT,
    expected_delivery   DATE,
    assigned_branch_id  BIGINT,
    assigned_hub        VARCHAR(120),
    courier_name        VARCHAR(120),
    courier_phone       VARCHAR(20),
    freight_charge      DECIMAL(10,2) NOT NULL DEFAULT 0,
    cod_amount          DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shipment_user FOREIGN KEY (booked_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_shipment_branch FOREIGN KEY (assigned_branch_id) REFERENCES branches(id) ON DELETE SET NULL,
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
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rate_cards (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_type    VARCHAR(30)   NOT NULL,
    min_weight_kg   DECIMAL(6,2)  NOT NULL DEFAULT 0,
    max_weight_kg   DECIMAL(6,2)  NOT NULL DEFAULT 999,
    base_rate       DECIMAL(10,2) NOT NULL,
    per_kg_rate     DECIMAL(10,2) NOT NULL DEFAULT 0,
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoices (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT        NOT NULL,
    invoice_number  VARCHAR(30)   NOT NULL UNIQUE,
    freight_amount  DECIMAL(10,2) NOT NULL DEFAULT 0,
    cod_amount      DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    notes           VARCHAR(255),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT,
    channel         VARCHAR(20)   NOT NULL,
    recipient       VARCHAR(150)  NOT NULL,
    subject         VARCHAR(200),
    body            VARCHAR(1000) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'SENT',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id   BIGINT,
    actor_email     VARCHAR(150),
    action          VARCHAR(80)   NOT NULL,
    entity_type     VARCHAR(40)   NOT NULL,
    entity_id       VARCHAR(40),
    details         VARCHAR(1000),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Upgrade existing databases (no-op when column already exists)
SET @db := DATABASE();

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN assigned_branch_id BIGINT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'assigned_branch_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN assigned_hub VARCHAR(120) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'assigned_hub'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN courier_name VARCHAR(120) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'courier_name'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN courier_phone VARCHAR(20) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'courier_phone'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN freight_charge DECIMAL(10,2) NOT NULL DEFAULT 0',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'freight_charge'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN cod_amount DECIMAL(10,2) NOT NULL DEFAULT 0',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'cod_amount'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE contact_messages ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''UNREAD''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'contact_messages' AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
