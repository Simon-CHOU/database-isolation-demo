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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadCommittedPhenomenaTest {
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
    }

    private void logLockInfo(String sessionName, int id) {
        try {
            String lockQuery = """
                SELECT pl.mode, pl.granted, t.* 
                FROM pg_locks pl 
                JOIN pg_stat_activity psa ON pl.pid = psa.pid 
                JOIN accounts t ON pl.relation = 'accounts'::regclass 
                WHERE t.id = ? AND pl.pid = pg_backend_pid();
            """;
            
            jdbcTemplate.query(lockQuery, (rs) -> {
                System.out.printf("[%s] 行锁状态 - ID=%d, 锁模式: %s, 是否获得锁: %s%n",
                    sessionName,
                    id,
                    rs.getString("mode"),
                    rs.getBoolean("granted") ? "是" : "否"
                );
            }, id);
        } catch (Exception e) {
            System.out.printf("[%s] 查询锁状态时发生错误: %s%n", sessionName, e.getMessage());
        }
    }

    @Test
    void testNonrepeatableRead() throws Exception {
        AtomicInteger anomalyCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            // 初始化测试数据
            jdbcTemplate.execute("DROP TABLE IF EXISTS accounts");
            jdbcTemplate.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2))");
            jdbcTemplate.update("INSERT INTO accounts VALUES (1, 100.00)");
            
            CountDownLatch startLatch = new CountDownLatch(1);
            
            // 事务1：读取-等待-再次读取
            CompletableFuture<Boolean> reader = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 第一次读取
                        BigDecimal balance1 = jdbcTemplate.queryForObject(
                            "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
                        logLockInfo("Reader第一次读取", 1);
                        
                        // 等待更新事务
                        TimeUnit.MILLISECONDS.sleep(100);
                        
                        // 第二次读取
                        BigDecimal balance2 = jdbcTemplate.queryForObject(
                            "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
                        logLockInfo("Reader第二次读取", 1);
                        
                        transactionManager.commit(status);
                        return !balance1.equals(balance2);
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 事务2：更新余额
            CompletableFuture<Void> updater = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    TimeUnit.MILLISECONDS.sleep(50);
                    
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        jdbcTemplate.update("UPDATE accounts SET balance = 200.00 WHERE id = 1");
                        logLockInfo("Updater", 1);
                        transactionManager.commit(status);
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            startLatch.countDown();
            
            if (reader.get(5, TimeUnit.SECONDS)) {
                anomalyCount.incrementAndGet();
            }
            updater.get(5, TimeUnit.SECONDS);
        }
        
        System.out.println("\n不可重复读测试结果:");
        System.out.println("发生不可重复读的次数: " + anomalyCount.get());
        System.out.println("总测试次数: 100");
    }

    @Test
    void testPhantomRead() throws Exception {
        AtomicInteger anomalyCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            // 初始化测试数据
            jdbcTemplate.execute("DROP TABLE IF EXISTS accounts");
            jdbcTemplate.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2))");
            jdbcTemplate.update("INSERT INTO accounts VALUES (1, 100.00)");
            
            CountDownLatch startLatch = new CountDownLatch(1);
            
            // 事务1：范围查询两次
            CompletableFuture<Boolean> reader = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 第一次范围查询
                        int count1 = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM accounts WHERE balance >= 100", Integer.class);
                        
                        // 等待插入事务
                        TimeUnit.MILLISECONDS.sleep(100);
                        
                        // 第二次范围查询
                        int count2 = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM accounts WHERE balance >= 100", Integer.class);
                        
                        transactionManager.commit(status);
                        return count1 != count2;
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 事务2：插入新记录
            CompletableFuture<Void> inserter = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await();
                    TimeUnit.MILLISECONDS.sleep(50);
                    
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        jdbcTemplate.update("INSERT INTO accounts VALUES (2, 150.00)");
                        transactionManager.commit(status);
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            startLatch.countDown();
            
            if (reader.get(5, TimeUnit.SECONDS)) {
                anomalyCount.incrementAndGet();
            }
            inserter.get(5, TimeUnit.SECONDS);
        }
        
        System.out.println("\n幻读测试结果:");
        System.out.println("发生幻读的次数: " + anomalyCount.get());
        System.out.println("总测试次数: 100");
    }

    @Test
    void testSerializationAnomaly() throws Exception {
        AtomicInteger anomalyCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            // 初始化测试数据
            jdbcTemplate.execute("DROP TABLE IF EXISTS accounts");
            jdbcTemplate.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance DECIMAL(10,2))");
            jdbcTemplate.update("INSERT INTO accounts VALUES (1, 100.00), (2, 100.00)");
            
            CountDownLatch startLatch = new CountDownLatch(1);
            
            // 事务1：转账 A->B
            CompletableFuture<Boolean> transfer1 = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 总是先获取较小ID的行锁
                        jdbcTemplate.update("UPDATE accounts SET balance = balance - 50 WHERE id = 1");
                        logLockInfo("Transfer1-扣款", 1);
                        TimeUnit.MILLISECONDS.sleep(50);
                        jdbcTemplate.update("UPDATE accounts SET balance = balance + 50 WHERE id = 2");
                        logLockInfo("Transfer1-入账", 2);
                        transactionManager.commit(status);
                        return true;
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        if (e.getCause() != null && e.getCause().getMessage().contains("deadlock detected")) {
                            return false;
                        }
                        throw e;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            
            // 事务2：转账 B->A
            CompletableFuture<Boolean> transfer2 = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    TransactionStatus status = transactionManager.getTransaction(def);
                    
                    try {
                        // 总是先获取较小ID的行锁
                        jdbcTemplate.update("UPDATE accounts SET balance = balance - 50 WHERE id = 1");
                        logLockInfo("Transfer2-扣款", 1);
                        TimeUnit.MILLISECONDS.sleep(50);
                        jdbcTemplate.update("UPDATE accounts SET balance = balance + 50 WHERE id = 2");
                        logLockInfo("Transfer2-入账", 2);
                        transactionManager.commit(status);
                        return true;
                    } catch (Exception e) {
                        transactionManager.rollback(status);
                        if (e.getCause() != null && e.getCause().getMessage().contains("deadlock detected")) {
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
            boolean t1Success = transfer1.get(5, TimeUnit.SECONDS);
            boolean t2Success = transfer2.get(5, TimeUnit.SECONDS);
            
            // 统计死锁次数
            if (!t1Success || !t2Success) {
                deadlockCount.incrementAndGet();
                continue;
            }
            
            // 检查最终余额是否正确
            BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT SUM(balance) FROM accounts", BigDecimal.class);
            if (total.compareTo(new BigDecimal("200.00")) != 0) {
                anomalyCount.incrementAndGet();
            }
        }
        
        System.out.println("\n序列化异常测试结果:");
        System.out.println("发生序列化异常的次数: " + anomalyCount.get());
        System.out.println("发生死锁的次数: " + deadlockCount.get());
        System.out.println("总测试次数: 100");
    }
}