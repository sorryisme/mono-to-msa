package com.sorryisme.fmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 생성/수정 일시 공통 매핑.
 *
 * <p>모든 테이블의 created_at/updated_at 은 DB 기본값(CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP)으로 채워진다.
 * 애플리케이션이 값을 덮어쓰지 않도록 읽기 전용으로 매핑하고, 저장 직후 값이 필요하면 재조회해야 한다.
 */
@MappedSuperclass
@Getter
public abstract class BaseTimeEntity {

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
