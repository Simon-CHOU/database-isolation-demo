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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializableIsolationTest {
    
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
    void testSerializable() throws Exception {
        // 设置事务隔离级别为SERIALIZABLE
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // 第一次读取
            BigDecimal initial = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在另一个线程中尝试更新数据
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 使用新的连接执行更新
                    Connection conn = dataSource.getConnection();
                    conn.setAutoCommit(false);
                    conn.createStatement().executeUpdate("UPDATE accounts SET balance = 150 WHERE id = 1");
                    conn.commit();
                    conn.close();
                    return true;
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 在同一事务中再次读取
            BigDecimal current = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            
            // 在SERIALIZABLE隔离级别下，应该读取到相同的值
            assertEquals(initial, current);
            
            // 提交第一个事务
            transactionManager.commit(status);
            
            // 检查第二个事务是否成功
            boolean secondTransactionSucceeded = future.get(2, TimeUnit.SECONDS);
            assertTrue(secondTransactionSucceeded, "第二个事务应该能够成功执行");
            
            // 验证最终的值已更新
            BigDecimal finalValue = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
            assertEquals(new BigDecimal("150.00"), finalValue);
        } catch (TimeoutException | ExecutionException e) {
            // 在某些数据库中，SERIALIZABLE可能导致死锁或超时
            transactionManager.rollback(status);
            throw e;
        }
    }
}