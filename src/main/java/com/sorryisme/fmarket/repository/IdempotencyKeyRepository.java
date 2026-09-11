package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {}
