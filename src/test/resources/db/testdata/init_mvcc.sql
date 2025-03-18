CREATE TABLE IF NOT EXISTS accounts (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  balance DECIMAL(10,2) NOT NULL,
  version BIGINT DEFAULT 0
);

TRUNCATE TABLE accounts;
INSERT INTO accounts (id, name, balance, version) VALUES
(1, 'Alice', 100.00, 0),
(2, 'Bob', 200.00, 0);