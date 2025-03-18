-- 删除表（如果存在）
DROP TABLE IF EXISTS accounts;

-- 创建表
CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    balance DECIMAL(10, 2) NOT NULL
);