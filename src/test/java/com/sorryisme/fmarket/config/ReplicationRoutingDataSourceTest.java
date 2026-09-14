package com.sorryisme.fmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sorryisme.fmarket.common.AppConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 읽기 전용 트랜잭션만 replica 로 보낸다. 이 분기가 뒤집히면 쓰기가 replica 로 가서 실패하거나, 조회가 전부 master 로 몰린다.
 *
 * <p>readOnly 표시는 스레드 로컬이므로 테스트마다 반드시 되돌려 다른 테스트에 새지 않게 한다.
 */
class ReplicationRoutingDataSourceTest {

  private final ReplicationRoutingDataSource dataSource = new ReplicationRoutingDataSource();

  @AfterEach
  void clearReadOnlyFlag() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
  }

  @Test
  @DisplayName("읽기 전용 트랜잭션이면 replica 를 고른다")
  void routesReadOnlyToReplica() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

    assertThat(dataSource.determineCurrentLookupKey()).isEqualTo(AppConstants.DATASOURCE_REPLICA);
  }

  @Test
  @DisplayName("읽기 전용이 아니면 source 를 고른다")
  void routesOthersToSource() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

    assertThat(dataSource.determineCurrentLookupKey()).isEqualTo(AppConstants.DATASOURCE_SOURCE);
  }
}
