package com.example.demo.isolev;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ReadCommittedIsolationTest {
    
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // 创建一个直接连接到数据库的DataSource
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://localhost:5432/testdb"); // 根据你的实际配置修改
        ds.setUsername("postgres"); // 根据你的实际配置修改
        ds.setPassword("postgres"); // 根据你的实际配置修改
        
        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        
        // 初始化测试数据
        jdbcTemplate.execute("DROP TABLE IF EXISTS accounts");
        jdbcTemplate.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2))");
        jdbcTemplate.update("INSERT INTO accounts VALUES (1, 100.00)");
    }

    @Test
    void testReadCommitted() throws Exception {
        // 设置事务隔离级别为READ_COMMITTED
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 第一次读取
            BigDecimal initial = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在另一个线程中更新数据
            CompletableFuture.runAsync(() -> {
                try {
                    // 使用新的连接执行更新
                    Connection conn = dataSource.getConnection();
                    conn.setAutoCommit(false);
                    conn.createStatement().executeUpdate("UPDATE accounts SET balance = 150 WHERE id = 1");
                    conn.commit();
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }).get(2, TimeUnit.SECONDS);
            
            // 在同一事务中再次读取
            BigDecimal current = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在READ_COMMITTED隔离级别下，应该读取到更新后的值
            assertNotEquals(initial, current);
            assertEquals(new BigDecimal("150.00"), current);
        } finally {
            transactionManager.commit(status);
        }
    }
}