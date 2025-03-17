-- drop TABLE if exists accounts;
CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL
);

INSERT INTO accounts (name, balance) VALUES ('Alice', 100.00);
INSERT INTO accounts (name, balance) VALUES ('Bob', 50.00);