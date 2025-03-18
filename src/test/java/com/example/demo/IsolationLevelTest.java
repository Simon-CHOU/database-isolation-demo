// package com.example.demo;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.jdbc.core.JdbcTemplate;
// import org.springframework.test.context.jdbc.Sql;
// import org.springframework.transaction.annotation.Isolation;
// import org.springframework.transaction.annotation.Transactional;
// import java.math.BigDecimal;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ExecutionException;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.TimeoutException;

// import static org.junit.jupiter.api.Assertions.*;
// import org.springframework.dao.DataAccessException;


// @SpringBootTest
// @Sql(scripts = "/db/testdata/init.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
// public class IsolationLevelTest {

//     @Autowired
//     private JdbcTemplate jdbcTemplate;

//     @Autowired
//     private AccountRepository accountRepository;

//     @BeforeEach
//     public void setUp() {
//         // 清空表并插入初始数据
//         jdbcTemplate.execute("TRUNCATE TABLE accounts;");
//         jdbcTemplate.execute("INSERT INTO accounts (name, balance) VALUES ('Alice', 100.00), ('Bob', 50.00);");
//     }

//     @Test
//     @Transactional(isolation = Isolation.READ_COMMITTED)
//     public void testReadCommitted() throws InterruptedException, ExecutionException {
//         // Session 1: 读取初始余额
//         BigDecimal initialBalance = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
//         assertEquals(new BigDecimal("100.00"), initialBalance);
//         System.out.println("--=================");
//         // Session 2: 异步更新余额 (使用CompletableFuture模拟并发)
//         CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//             jdbcTemplate.update("UPDATE accounts SET balance = balance + 50.00 WHERE id = 1");
//             jdbcTemplate.execute("COMMIT"); //手动提交
//         });
//         TimeUnit.MILLISECONDS.sleep(500);
//         System.out.println("---------=================");
//         // Session 1: 再次读取余额，应该能看到Session 2提交的更新
//         BigDecimal updatedBalance = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
//         System.out.println("----1111-----===========###########======");
//         try {
//             future.get(2, TimeUnit.SECONDS);
//             fail("Expected exception not thrown");
//         } catch (TimeoutException e) {
//             fail("Test timed out: " + e.getMessage());
//         }
//         System.out.println("---------===========###########======");
//         assertEquals(new BigDecimal("150.00"), updatedBalance);
//     }


//     @Test
//     @Transactional(isolation = Isolation.READ_COMMITTED)
//     public void testReadCommitted_PreventDirtyRead() throws Exception {
//         BigDecimal balance1 = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1;", BigDecimal.class);
//         assertEquals(new BigDecimal("100.00"), balance1);


//         CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//             //在另一个事务中更新
//             jdbcTemplate.update("UPDATE accounts SET balance = balance + 50.00 WHERE id = 1;");
//             // 不提交
//             try {
//                 TimeUnit.SECONDS.sleep(5);  //模拟长时间运行的事务
//             } catch (InterruptedException e) {
//                 Thread.currentThread().interrupt();
//             }
//         });
//         TimeUnit.MILLISECONDS.sleep(500);
//         BigDecimal balance2 = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1;", BigDecimal.class);
//         assertEquals(new BigDecimal("100.00"), balance2);
//     }


//     @Test
//     @Transactional(isolation = Isolation.REPEATABLE_READ)
//     public void testRepeatableRead() throws InterruptedException, ExecutionException {
//         BigDecimal initialBalance = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
//         assertEquals(new BigDecimal("100.00"), initialBalance);

//         CompletableFuture<Void> future = CompletableFuture.runAsync(()->{
//                 // 在另一个事务中更新余额并提交
//                 jdbcTemplate.update("UPDATE accounts SET balance = balance + 50.00 WHERE id = 1");
//                 jdbcTemplate.execute("commit");

//         });
//         TimeUnit.MILLISECONDS.sleep(500);
//         BigDecimal balance2 = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1;", BigDecimal.class);
//         assertEquals(new BigDecimal("100.00"), balance2); //仍然是100
//     }


//     @Test
//     public void testSerializable_PhantomRead() {

//         assertThrows(DataAccessException.class, () -> { // 使用assertThrows捕获预期异常

//             // Session 1
//             CompletableFuture<Void> session1 = CompletableFuture.runAsync(() -> {
//                 // 设置事务隔离级别为SERIALIZABLE
//                 jdbcTemplate.execute("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE");
//                 jdbcTemplate.execute("BEGIN");

//                 // 第一次查询
//                 jdbcTemplate.queryForList("SELECT * FROM accounts WHERE balance > 80.00");

//                 try {
//                     TimeUnit.SECONDS.sleep(2); // 等待session2插入数据
//                 } catch (InterruptedException e) {
//                     Thread.currentThread().interrupt();
//                 }

//                 // 第二次查询，在REPEATABLE_READ下会发生幻读，但在SERIALIZABLE下会抛出异常
//                 jdbcTemplate.queryForList("SELECT * FROM accounts WHERE balance > 80.00");
//                 jdbcTemplate.execute("COMMIT");

//             });

//             // Session 2
//             CompletableFuture<Void> session2 = CompletableFuture.runAsync(() -> {
//                 // 设置事务隔离级别为SERIALIZABLE
//                 jdbcTemplate.execute("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE");
//                 jdbcTemplate.execute("BEGIN");

//                 try {
//                     TimeUnit.SECONDS.sleep(1); // 稍作延迟，确保session1先执行第一次查询
//                 } catch (InterruptedException e) {
//                     Thread.currentThread().interrupt();
//                 }

//                 // 插入新数据，这会导致session1的第二次查询产生幻读(如果在REPEATABLE_READ下)
//                 jdbcTemplate.update("INSERT INTO accounts (name, balance) VALUES ('Charlie', 90.00)");
//         jdbcTemplate.execute("COMMIT"); // 确保在session1第二次查询前提交
//                 jdbcTemplate.execute("COMMIT"); // 提交事务
//             });

//              session1.get();
//              session2.get();

//         });

//     }

//     @Test
//     @Transactional
//     public void testSerializable_Conflict() {
//         //使用JPA
//         assertThrows(DataAccessException.class, () -> {
//             CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {

//                 accountRepository.findById(1L); // 触发读取
//                 try {
//                     TimeUnit.SECONDS.sleep(1); // 等待另一个事务
//                 } catch (InterruptedException e) {
//                     Thread.currentThread().interrupt();
//                 }
//                 accountRepository.updateBalance(1L, new BigDecimal("200.00")); // 基于初始读取的更新
//                 accountRepository.flush(); // 强制提交更改


//             });

//             CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
//                 accountRepository.findById(2L); // 触发读取
//                 try {
//                     TimeUnit.SECONDS.sleep(1);
//                 } catch (InterruptedException e) {
//                     Thread.currentThread().interrupt();
//                 }
//                 accountRepository.updateBalance(2L, new BigDecimal("10.00"));// 基于初始读取的更新
//                 accountRepository.flush(); //强制提交更改
//             });

//             future1.get();
//             future2.get();
//         });
//     }

// }