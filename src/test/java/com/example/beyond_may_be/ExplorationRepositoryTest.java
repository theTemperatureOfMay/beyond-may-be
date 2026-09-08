package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.common.config.JpaAuditingConfig;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import java.time.LocalDateTime;
import java.util.List;
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
  @Autowired private ExplorationParticipantRepository explorationParticipantRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @DisplayName("코스 ID에 연결된 탐험을 조회하고 없으면 빈 결과를 반환한다.")
  @Test
  void findByCourseId_returnsOnlyMatchingExploration() {
    Exploration matching =
        explorationRepository.saveAndFlush(
            Exploration.builder().courseId(999995L).status(ExplorationStatus.BEFORE).build());
    explorationRepository.saveAndFlush(
        Exploration.builder().courseId(999996L).status(ExplorationStatus.COMPLETED).build());

    assertThat(explorationRepository.findByCourseId(999995L))
        .map(Exploration::getId)
        .contains(matching.getId());
    assertThat(explorationRepository.findByCourseId(999997L)).isEmpty();
  }

  @DisplayName("현재 또는 과거 참여자의 완료 탐험을 완료 시각 내림차순으로 조회한다.")
  @Test
  void findAllByParticipantUserIdAndStatus_returnsMembershipHistoryInCompletionOrder() {
    Exploration older = completedExploration(999991L, LocalDateTime.of(2026, 8, 10, 19, 10));
    Exploration newer = completedExploration(999992L, LocalDateTime.of(2026, 8, 15, 18, 20));
    Exploration ongoing =
        explorationRepository.save(
            Exploration.builder()
                .courseId(999993L)
                .status(ExplorationStatus.ONGOING)
                .startedAt(LocalDateTime.of(2026, 8, 16, 10, 0))
                .build());
    Exploration anotherUsers = completedExploration(999994L, LocalDateTime.of(2026, 8, 20, 18, 20));
    explorationParticipantRepository.saveAll(
        List.of(
            participant(older.getId(), 71L, ParticipantStatus.LEFT),
            participant(newer.getId(), 71L, ParticipantStatus.COMPLETED),
            participant(ongoing.getId(), 71L, ParticipantStatus.ACTIVE),
            participant(anotherUsers.getId(), 72L, ParticipantStatus.COMPLETED)));

    List<Exploration> result =
        explorationRepository.findAllByParticipantUserIdAndStatus(71L, ExplorationStatus.COMPLETED);

    assertThat(result).extracting(Exploration::getId).containsExactly(newer.getId(), older.getId());
  }

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

  private Exploration completedExploration(long courseId, LocalDateTime completedAt) {
    return explorationRepository.save(
        Exploration.builder()
            .courseId(courseId)
            .status(ExplorationStatus.COMPLETED)
            .startedAt(completedAt.minusHours(8))
            .completedAt(completedAt)
            .build());
  }

  private ExplorationParticipant participant(
      Long explorationId, Long userId, ParticipantStatus status) {
    return ExplorationParticipant.builder()
        .explorationId(explorationId)
        .userId(userId)
        .role(ParticipantRole.MEMBER)
        .status(status)
        .displayName("여행자 " + userId)
        .locationSharingEnabled(false)
        .joinedAt(LocalDateTime.of(2026, 8, 1, 10, 0))
        .build();
  }
}
