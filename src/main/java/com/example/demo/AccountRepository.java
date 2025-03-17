package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    // 可以根据需要添加自定义查询方法

    @Modifying
    @Query("UPDATE Account SET balance = :newBalance WHERE id = :id")
    void updateBalance(@Param("id") Long id, @Param("newBalance") BigDecimal newBalance);

}