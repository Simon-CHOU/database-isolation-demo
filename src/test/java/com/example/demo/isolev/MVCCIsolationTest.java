package com.example.demo.isolev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Sql(scripts = "/db/testdata/init_mvcc.sql")
public class MVCCIsolationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void testMVCCSnapshotIsolation() throws Exception {
        // 获取初始版本号
        Integer initialVersion = jdbcTemplate.queryForObject(
            "SELECT version FROM accounts WHERE id = 1", Integer.class);

        CompletableFuture.runAsync(() -> {
            jdbcTemplate.update("UPDATE accounts SET balance = 150, version = version + 1 WHERE id = 1");
        }).get(2, TimeUnit.SECONDS);

        // 在原始事务中再次查询，版本号应保持不变
        Integer currentVersion = jdbcTemplate.queryForObject(
            "SELECT version FROM accounts WHERE id = 1", Integer.class);
        
        assertEquals(initialVersion, currentVersion);
    }
}