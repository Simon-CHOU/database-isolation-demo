package com.example.demo.mt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SerializationAnomalyDocTest {
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://localhost:5432/yourdb");
        ds.setUsername("youruser");
        ds.setPassword("yourpassword");
        
        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionManager = new DataSourceTransactionManager(dataSource);
        
        // 初始化测试数据
        jdbcTemplate.execute("DROP TABLE IF EXISTS mytab");
        jdbcTemplate.execute("""
            CREATE TABLE mytab (
                class INT,
                value INT
            )
        """);
        // 插入初始数据
        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (1, 10)");
        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (1, 20)");
        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (2, 100)");
        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (2, 200)");
    }

    private void logLockInfo(String sessionName, int classValue) {
        try {
            String lockQuery = """
                SELECT pl.mode, pl.granted, t.* 
                FROM pg_locks pl 
                JOIN pg_stat_activity psa ON pl.pid = psa.pid 
                JOIN mytab t ON pl.relation = 'mytab'::regclass 
                WHERE t.class = ? AND pl.pid = pg_backend_pid();
            """;
            
            jdbcTemplate.query(lockQuery, (rs) -> {
                System.out.printf("[%s] 行锁状态 - class=%d, 锁模式: %s, 是否获得锁: %s%n",
                    sessionName,
                    classValue,
                    rs.getString("mode"),
                    rs.getBoolean("granted") ? "是" : "否"
                );
            }, classValue);
        } catch (Exception e) {
            System.out.printf("[%s] 查询锁状态时发生错误: %s%n", sessionName, e.getMessage());
        }
    }

    @Test
    void testSerializationAnomaly() throws Exception {
        AtomicInteger serializationErrorCount = new AtomicInteger(0);
        AtomicInteger illegalTransactionStateCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            // 每次测试前重置数据
            jdbcTemplate.update("TRUNCATE TABLE mytab");
            jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (1, 10), (1, 20), (2, 100), (2, 200)");
            
            CountDownLatch startLatch = new CountDownLatch(1);
            
            // 事务A：计算class=1的sum，并插入到class=2
            CompletableFuture<Boolean> transactionA = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 计算class=1的总和
                        Integer sum = jdbcTemplate.queryForObject(
                            "SELECT SUM(value) FROM mytab WHERE class = 1", Integer.class);
                        logLockInfo("Transaction A - 读取", 1);
                        
                        // 模拟一些处理时间
                        TimeUnit.MILLISECONDS.sleep(50);
                        
                        // 将结果插入class=2
                        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (2, ?)", sum);
                        logLockInfo("Transaction A - 写入", 2);
                        
                        transactionManager.commit(status);
                        // 故意触发重复提交
                        transactionManager.commit(status);
                        return true;
                    } catch (Exception e) {
                        try {
                            transactionManager.rollback(status);
                        } catch (IllegalTransactionStateException itse) {
                            illegalTransactionStateCount.incrementAndGet();
                            // 验证异常消息
                            assert itse.getMessage().contains("Transaction is already completed") : 
                                "Unexpected error message: " + itse.getMessage();
                        }
                        if (e.getMessage() != null && e.getMessage().contains("could not serialize access")) {
                            return false;
                        }
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 事务B：计算class=2的sum，并插入到class=1
            CompletableFuture<Boolean> transactionB = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 计算class=2的总和
                        Integer sum = jdbcTemplate.queryForObject(
                            "SELECT SUM(value) FROM mytab WHERE class = 2", Integer.class);
                        logLockInfo("Transaction B - 读取", 2);
                        
                        // 模拟一些处理时间
                        TimeUnit.MILLISECONDS.sleep(50);
                        
                        // 将结果插入class=1
                        jdbcTemplate.update("INSERT INTO mytab (class, value) VALUES (1, ?)", sum);
                        logLockInfo("Transaction B - 写入", 1);
                        
                        transactionManager.commit(status);
                        return true;
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        if (e.getMessage().contains("could not serialize access")) {
                            return false;
                        }
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            startLatch.countDown();
            
            // 等待两个事务完成
            boolean aSuccess = transactionA.get(5, TimeUnit.SECONDS);
            boolean bSuccess = transactionB.get(5, TimeUnit.SECONDS);
            
            // 如果有一个事务失败了，说明发生了序列化错误
            if (!aSuccess || !bSuccess) {
                serializationErrorCount.incrementAndGet();
            }
            
            // 打印当前测试结果
            System.out.printf("测试 #%d: Transaction A %s, Transaction B %s%n",
                i + 1,
                aSuccess ? "提交成功" : "回滚",
                bSuccess ? "提交成功" : "回滚"
            );
        }
        
        // 打印最终统计结果
        System.out.println("\n测试结果统计:");
        System.out.println("发生序列化错误的次数: " + serializationErrorCount.get());
        System.out.println("发生事务状态异常的次数: " + illegalTransactionStateCount.get());
        System.out.println("总测试次数: 100");
        
        // 验证是否捕获到了预期的异常
        assert illegalTransactionStateCount.get() > 0 : 
            "Expected to catch IllegalTransactionStateException but none was thrown";
    }
}