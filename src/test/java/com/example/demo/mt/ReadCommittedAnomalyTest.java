package com.example.demo.mt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * <a href="https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED">PG 读已提交- 幻读 验证</a>
 */
public class ReadCommittedAnomalyTest {
    
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // 创建一个直接连接到数据库的DataSource
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://localhost:5432/yourdb"); // 根据你的实际配置修改
        ds.setUsername("youruser"); // 根据你的实际配置修改
        ds.setPassword("yourpassword"); // 根据你的实际配置修改
        
        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        
        // 初始化测试数据
        jdbcTemplate.execute("DROP TABLE IF EXISTS website");
        jdbcTemplate.execute("CREATE TABLE website (id INT PRIMARY KEY, hits INT)");
    }

    @Test
    void testReadCommittedAnomaly() throws Exception {
        // 统计DELETE操作生效和不生效的次数
        AtomicInteger deleteSuccessCount = new AtomicInteger(0);
        AtomicInteger deleteFailCount = new AtomicInteger(0);
        
        // 执行100次测试
        for (int i = 0; i < 1000; i++) {
            // 每次测试前重置数据
            jdbcTemplate.update("TRUNCATE TABLE website");
            jdbcTemplate.update("INSERT INTO website VALUES (1, 9), (2, 10)");
            
            // 使用CountDownLatch确保两个事务几乎同时开始
            CountDownLatch startLatch = new CountDownLatch(1);
            
            // 第一个事务：UPDATE操作
            CompletableFuture<Void> updateFuture = CompletableFuture.runAsync(() -> {
                try {
                    // 等待信号开始执行
                    startLatch.await();
                    
                    // 设置事务隔离级别为READ_COMMITTED
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 更新所有行的hits值
                        jdbcTemplate.update("UPDATE website SET hits = hits + 1");
                        
                        // 模拟一些处理时间，增加并发冲突的可能性
                        TimeUnit.MILLISECONDS.sleep(10);
                        
                        // 提交事务
                        transactionManager.commit(status);
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            // 第二个事务：DELETE操作
            CompletableFuture<Boolean> deleteFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // 等待信号开始执行
                    startLatch.await();
                    
                    // 稍微延迟，让UPDATE有机会先执行
                    TimeUnit.MILLISECONDS.sleep(5);
                    
                    // 设置事务隔离级别为READ_COMMITTED
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 删除hits=10的行
                        int rowsAffected = jdbcTemplate.update("DELETE FROM website WHERE hits = 10");
                        
                        // 提交事务
                        transactionManager.commit(status);
                        
                        // 返回是否有行被删除
                        return rowsAffected > 0;
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 发出信号，让两个事务开始执行
            startLatch.countDown();
            
            // 等待两个事务完成
            updateFuture.get(5, TimeUnit.SECONDS);
            boolean deleteSucceeded = deleteFuture.get(5, TimeUnit.SECONDS);
            
            // 统计结果
            if (deleteSucceeded) {
                deleteSuccessCount.incrementAndGet();
            } else {
                deleteFailCount.incrementAndGet();
            }
            
            // 验证最终状态
            int remainingRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM website", Integer.class);
            System.out.printf("测试 #%d: DELETE %s, 剩余行数: %d%n", 
                    i + 1, 
                    deleteSucceeded ? "生效" : "不生效", 
                    remainingRows);
        }
        
        // 输出最终统计结果
        System.out.println("\n测试结果统计:");
        System.out.println("DELETE 操作生效次数: " + deleteSuccessCount.get());
        System.out.println("DELETE 操作不生效次数: " + deleteFailCount.get());
        System.out.println("总测试次数: " + (deleteSuccessCount.get() + deleteFailCount.get()));
        // 测试结果统计:
        //DELETE 操作生效次数: 19
        //DELETE 操作不生效次数: 981
        //总测试次数: 1000
    }
}
