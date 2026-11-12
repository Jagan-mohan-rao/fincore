CREATE TABLE customers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_customers_email UNIQUE (email)
);

CREATE TABLE accounts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  account_number CHAR(12) NOT NULL,
  account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
  balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
  currency CHAR(3) NOT NULL DEFAULT 'USD',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_accounts_number UNIQUE (account_number),
  CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
  INDEX idx_accounts_customer_created (customer_id, created_at DESC)
);

CREATE TABLE transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reference CHAR(36) NOT NULL,
  account_id BIGINT NOT NULL,
  counterparty_account_id BIGINT NULL,
  transaction_type VARCHAR(20) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  balance_after DECIMAL(19,2) NOT NULL,
  description VARCHAR(180) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_transactions_reference (reference),
  CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_transactions_counterparty FOREIGN KEY (counterparty_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_transactions_amount CHECK (amount > 0),
  INDEX idx_transactions_account_created (account_id, created_at DESC, id DESC),
  INDEX idx_transactions_counterparty (counterparty_account_id)
);
