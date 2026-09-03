package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.domain.VisitPhoto;
import com.example.beyond_may_be.visit.repository.VisitPhotoRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VisitRepositoryTest {

  @Autowired private VisitRepository visitRepository;
  @Autowired private VisitPhotoRepository visitPhotoRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void countsUniqueCourseAndNearbyPlacesByParticipant() {
    visitRepository.saveAll(
        List.of(visit(70L, 1L, 101L), visit(70L, 2L, null), visit(71L, 1L, 101L)));

    Map<Long, Long> counts =
        visitRepository.countByParticipantIds(List.of(70L, 71L)).stream()
            .collect(
                Collectors.toMap(
                    VisitRepository.ParticipantVisitCount::getParticipantId,
                    VisitRepository.ParticipantVisitCount::getVisitCount));

    assertThat(counts).containsEntry(70L, 2L).containsEntry(71L, 1L);
    assertThat(visitRepository.countDistinctCoursePlaceIds(List.of(70L, 71L))).isEqualTo(1L);
    assertThat(visitRepository.existsByParticipantIdAndPlaceId(70L, 1L)).isTrue();
    assertThat(visitRepository.existsByPlaceIdAndParticipantIdIn(2L, List.of(70L, 71L))).isTrue();
  }

  @DisplayName("팀 방문은 최신순으로, 각 방문 사진은 표시 순서대로 조회한다.")
  @Test
  void teamVisitQueries_returnRequiredOrder() {
    Visit older =
        visitRepository.saveAndFlush(
            Visit.builder()
                .participantId(92L)
                .placeId(900003L)
                .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
                .build());
    Visit newer =
        visitRepository.saveAndFlush(
            Visit.builder()
                .participantId(93L)
                .placeId(900004L)
                .visitedAt(LocalDateTime.of(2026, 8, 15, 15, 5, 40))
                .build());
    visitPhotoRepository.saveAllAndFlush(List.of(photo(older.getId(), 2), photo(older.getId(), 1)));

    assertThat(visitRepository.findByParticipantIdInOrderByVisitedAtDesc(List.of(92L, 93L)))
        .extracting(Visit::getId)
        .containsExactly(newer.getId(), older.getId());
    assertThat(
            visitPhotoRepository.findByVisitIdInOrderByVisitIdAscDisplayOrderAsc(
                List.of(older.getId())))
        .extracting(VisitPhoto::getDisplayOrder)
        .containsExactly(1, 2);
  }

  @DisplayName("방문 사진 표시 순서는 최댓값 다음으로 계산되고 중복될 수 없다.")
  @Test
  void visitPhotoDisplayOrder_isUniquePerVisit() {
    Visit visit = visitRepository.saveAndFlush(visit(90L, 900001L, null));
    visitPhotoRepository.saveAndFlush(photo(visit.getId(), 1));

    assertThat(visitPhotoRepository.findMaxDisplayOrderByVisitId(visit.getId())).isEqualTo(1);
    assertThatThrownBy(() -> visitPhotoRepository.saveAndFlush(photo(visit.getId(), 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @DisplayName("방문 잠금은 같은 방문의 동시 사진 순서 배정을 직렬화한다.")
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void findByIdForUpdate_serializesConcurrentPhotoOrdering() throws Exception {
    Visit visit = visitRepository.saveAndFlush(visit(91L, 900002L, null));
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
                            visitRepository.findByIdForUpdate(visit.getId()).orElseThrow();
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
                            visitRepository.findByIdForUpdate(visit.getId()).orElseThrow();
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
      visitRepository.deleteById(visit.getId());
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

  private VisitPhoto photo(long visitId, int displayOrder) {
    return VisitPhoto.builder()
        .visitId(visitId)
        .objectKey("visits/" + visitId + "/" + displayOrder)
        .displayOrder(displayOrder)
        .build();
  }

  private Visit visit(long participantId, long placeId, Long coursePlaceId) {
    return Visit.builder()
        .participantId(participantId)
        .placeId(placeId)
        .coursePlaceId(coursePlaceId)
        .visitedAt(LocalDateTime.of(2026, 8, 15, 11, 0))
        .build();
  }
}
