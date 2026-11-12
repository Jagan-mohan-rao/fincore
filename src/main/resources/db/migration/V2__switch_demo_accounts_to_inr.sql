ALTER TABLE accounts ALTER COLUMN currency SET DEFAULT 'INR';
UPDATE accounts SET currency = 'INR' WHERE currency = 'USD';
