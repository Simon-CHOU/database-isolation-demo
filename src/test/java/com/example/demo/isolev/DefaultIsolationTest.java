package com.example.demo.isolev;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Sql(scripts = "/db/testdata/init_default.sql")
public class DefaultIsolationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(isolation = Isolation.DEFAULT)
    void testDefaultIsolation() throws Exception {
        BigDecimal initial = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
        
        CompletableFuture.runAsync(() -> {
            jdbcTemplate.update("UPDATE accounts SET balance = 150 WHERE id = 1");
        }).get(2, TimeUnit.SECONDS);

        BigDecimal updated = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = 1", BigDecimal.class);
        
        assertEquals(new BigDecimal("150.00"), updated);
    }
}