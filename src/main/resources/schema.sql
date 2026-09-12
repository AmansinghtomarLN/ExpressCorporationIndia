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
    address         VARCHAR(255),
    branch_category VARCHAR(20)  NOT NULL DEFAULT 'DOMESTIC'
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

CREATE TABLE IF NOT EXISTS parties (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_name      VARCHAR(150) NOT NULL,
    contact_person  VARCHAR(120),
    phone           VARCHAR(20)  NOT NULL,
    email           VARCHAR(150),
    gstin           VARCHAR(20),
    address         VARCHAR(255),
    city            VARCHAR(80)  NOT NULL,
    state           VARCHAR(80),
    pincode         VARCHAR(10),
    notes           VARCHAR(500),
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_parties_name (party_name),
    KEY idx_parties_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consignment_ranges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_id        BIGINT       NOT NULL,
    range_start     BIGINT       NOT NULL,
    range_end       BIGINT       NOT NULL,
    notes           VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_range_party FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE,
    KEY idx_range_party (party_id),
    KEY idx_range_span (range_start, range_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS manifests (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    manifest_number VARCHAR(30)  NOT NULL UNIQUE,
    party_id        BIGINT       NOT NULL,
    manifest_date   DATE         NOT NULL,
    through_name    VARCHAR(120),
    origin_city     VARCHAR(80),
    service_type    VARCHAR(30)  NOT NULL DEFAULT 'DOMESTIC_STANDARD',
    remarks         VARCHAR(500),
    total_boxes     INT          NOT NULL DEFAULT 0,
    total_weight    DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_manifest_party FOREIGN KEY (party_id) REFERENCES parties(id),
    KEY idx_manifests_date (manifest_date),
    KEY idx_manifests_party (party_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS manifest_items (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    manifest_id      BIGINT       NOT NULL,
    serial_no        INT          NOT NULL,
    consignment_no   VARCHAR(20)  NOT NULL,
    destination_city VARCHAR(80)  NOT NULL,
    number_of_boxes  INT          NOT NULL DEFAULT 1,
    weight_kg        DECIMAL(8,2) NOT NULL,
    receiver_name    VARCHAR(150) NOT NULL,
    receiver_phone   VARCHAR(20),
    shipment_id      BIGINT,
    CONSTRAINT fk_item_manifest FOREIGN KEY (manifest_id) REFERENCES manifests(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE SET NULL,
    UNIQUE KEY uk_consignment_no (consignment_no),
    KEY idx_item_manifest (manifest_id)
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

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN party_id BIGINT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'party_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN manifest_id BIGINT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'manifest_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN number_of_boxes INT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'number_of_boxes'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE branches ADD COLUMN branch_category VARCHAR(20) NOT NULL DEFAULT ''DOMESTIC''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'branches' AND COLUMN_NAME = 'branch_category'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE manifests ADD COLUMN billing_lane VARCHAR(20) NOT NULL DEFAULT ''AUTO''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'manifests' AND COLUMN_NAME = 'billing_lane'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE shipments ADD COLUMN billing_lane VARCHAR(20) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'billing_lane'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS billing_tariffs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    lane_type       VARCHAR(20)   NOT NULL UNIQUE,
    min_charge      DECIMAL(10,2) NOT NULL DEFAULT 0,
    base_rate       DECIMAL(10,2) NOT NULL DEFAULT 0,
    per_kg_rate     DECIMAL(10,2) NOT NULL DEFAULT 0,
    per_box_rate    DECIMAL(10,2) NOT NULL DEFAULT 0,
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    notes           VARCHAR(255),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cno_settings (
    id              BIGINT PRIMARY KEY,
    series_start    BIGINT NOT NULL DEFAULT 30000,
    series_end      BIGINT NOT NULL DEFAULT 199999,
    bucket_size     INT    NOT NULL DEFAULT 100,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE branches ADD COLUMN per_kg_rate DECIMAL(10,2) NOT NULL DEFAULT 8.00',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'branches' AND COLUMN_NAME = 'per_kg_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE branches ADD COLUMN per_box_rate DECIMAL(10,2) NOT NULL DEFAULT 10.00',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'branches' AND COLUMN_NAME = 'per_box_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE invoices ADD COLUMN billed_branch_id BIGINT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'billed_branch_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE invoices ADD COLUMN weight_kg DECIMAL(8,2) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'weight_kg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE invoices ADD COLUMN number_of_boxes INT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'number_of_boxes'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE invoices ADD COLUMN per_kg_rate DECIMAL(10,2) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'per_kg_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE invoices ADD COLUMN per_box_rate DECIMAL(10,2) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'per_box_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE manifests ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''CREATED''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'manifests' AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE manifests ADD COLUMN destination_branch_id BIGINT NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'manifests' AND COLUMN_NAME = 'destination_branch_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'manifests' AND COLUMN_NAME = 'party_id') = 'NO',
    'ALTER TABLE manifests MODIFY party_id BIGINT NULL',
    'SELECT 1')
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE parties ADD COLUMN per_kg_rate DECIMAL(10,2) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parties' AND COLUMN_NAME = 'per_kg_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE parties ADD COLUMN per_box_rate DECIMAL(10,2) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parties' AND COLUMN_NAME = 'per_box_rate'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS manifest_bills (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_number      VARCHAR(40)   NOT NULL UNIQUE,
    bill_type        VARCHAR(20)   NOT NULL,
    manifest_id      BIGINT        NOT NULL,
    party_id         BIGINT        NULL,
    branch_id        BIGINT        NULL,
    weight_kg        DECIMAL(10,2) NOT NULL DEFAULT 0,
    number_of_boxes  INT           NOT NULL DEFAULT 0,
    per_kg_rate      DECIMAL(10,2) NOT NULL DEFAULT 0,
    per_box_rate     DECIMAL(10,2) NOT NULL DEFAULT 0,
    freight_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    notes            VARCHAR(255),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_manifest FOREIGN KEY (manifest_id) REFERENCES manifests(id) ON DELETE CASCADE,
    KEY idx_bills_type (bill_type),
    KEY idx_bills_party (party_id),
    KEY idx_bills_branch (branch_id),
    KEY idx_bills_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
