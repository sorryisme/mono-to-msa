package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * idempotency_keys 테이블.
 *
 * <p>중복 판정은 idempotency_key 유니크 제약에 맡긴다. 두 번째 저장은 DataIntegrityViolationException 으로 실패하며, 그것이 곧
 * "중복 요청" 신호다.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", unique = true, columnDefinition = "CHAR(36)")
  private String idempotencyKey;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public IdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    this.createdAt = LocalDateTime.now();
  }
}
