CREATE TABLE IF NOT EXISTS accounts (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  balance DECIMAL(10,2) NOT NULL
);

TRUNCATE TABLE accounts;
INSERT INTO accounts (id, name, balance) VALUES
(1, 'Alice', 100.00),
(2, 'Bob', 200.00);