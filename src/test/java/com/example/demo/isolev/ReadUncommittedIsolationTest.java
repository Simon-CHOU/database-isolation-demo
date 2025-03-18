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

public class ReadUncommittedIsolationTest {
    
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
    void testReadUncommitted() throws Exception {
        // 设置事务隔离级别为READ_UNCOMMITTED
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 第一次读取
            BigDecimal initial = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在另一个线程中更新数据，但不提交
            CompletableFuture<Connection> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Connection conn = dataSource.getConnection();
                    conn.setAutoCommit(false);
                    conn.createStatement().executeUpdate("UPDATE accounts SET balance = 150 WHERE id = 1");
                    // 注意这里不提交事务
                    return conn;
                } catch (SQLException e) {
                    e.printStackTrace();
                    return null;
                }
            });
            
            Connection conn = future.get(2, TimeUnit.SECONDS);
            
            // 在同一事务中再次读取
            BigDecimal current = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在READ_UNCOMMITTED隔离级别下，应该能读取到未提交的更新
            assertNotEquals(initial, current);
            assertEquals(new BigDecimal("150.00"), current);
            
            // 清理：回滚未提交的事务并关闭连接
            if (conn != null) {
                conn.rollback();
                conn.close();
            }
        } finally {
            transactionManager.commit(status);
        }
    }
}