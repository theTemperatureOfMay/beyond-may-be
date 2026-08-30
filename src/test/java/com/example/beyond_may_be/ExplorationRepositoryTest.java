package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.common.config.JpaAuditingConfig;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@DataJpaTest
@Transactional
class ExplorationRepositoryTest {

  @Autowired private ExplorationRepository explorationRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @DisplayName("시작 전 탐험은 한 번만 진행 중으로 조건부 갱신된다.")
  @Test
  void startIfBefore_updatesOnlyOnce() {
    Exploration exploration =
        explorationRepository.saveAndFlush(
            Exploration.builder().courseId(999999L).status(ExplorationStatus.BEFORE).build());
    LocalDateTime startedAt = LocalDateTime.of(2026, 8, 15, 10, 0);

    int firstUpdate = explorationRepository.startIfBefore(exploration.getId(), 72L, startedAt);
    int secondUpdate = explorationRepository.startIfBefore(exploration.getId(), 73L, startedAt);
    Exploration updated = explorationRepository.findById(exploration.getId()).orElseThrow();

    assertThat(firstUpdate).isEqualTo(1);
    assertThat(secondUpdate).isZero();
    assertThat(updated.getStatus()).isEqualTo(ExplorationStatus.ONGOING);
    assertThat(updated.getStartedByParticipantId()).isEqualTo(72L);
    assertThat(updated.getStartedAt()).isEqualTo(startedAt);
  }

  @DisplayName("합류 인원 스냅샷을 읽는 탐험 잠금은 동시 접근을 직렬화한다.")
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void findByCourseIdForUpdate_serializesConcurrentAccess() throws Exception {
    Exploration exploration =
        explorationRepository.saveAndFlush(
            Exploration.builder().courseId(999998L).status(ExplorationStatus.BEFORE).build());
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    CountDownLatch secondLocked = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      var first =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            explorationRepository.findByCourseIdForUpdate(999998L).orElseThrow();
                            firstLocked.countDown();
                            await(releaseFirst);
                          }));
      assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

      var second =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            secondStarted.countDown();
                            explorationRepository.findByCourseIdForUpdate(999998L).orElseThrow();
                            secondLocked.countDown();
                          }));
      assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(secondLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();

      releaseFirst.countDown();
      first.get(5, TimeUnit.SECONDS);
      assertThat(secondLocked.await(5, TimeUnit.SECONDS)).isTrue();
      second.get(5, TimeUnit.SECONDS);
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
      explorationRepository.deleteById(exploration.getId());
    }
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
