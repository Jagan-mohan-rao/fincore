-- Inspect the indexed account history path. On a populated dataset, EXPLAIN ANALYZE
-- shows actual rows and timing as well as the selected access path.
EXPLAIN ANALYZE
SELECT t.id, t.reference, t.transaction_type, t.amount, t.balance_after,
       t.description, t.created_at, ca.account_number AS counterparty_account_number
FROM transactions t
LEFT JOIN accounts ca ON ca.id = t.counterparty_account_id
WHERE t.account_id = 1
ORDER BY t.created_at DESC, t.id DESC
LIMIT 20 OFFSET 0;

-- Customer account listing uses idx_accounts_customer_created.
EXPLAIN ANALYZE
SELECT id, account_number, account_type, balance, currency, status, created_at
FROM accounts
WHERE customer_id = 1
ORDER BY created_at DESC;
