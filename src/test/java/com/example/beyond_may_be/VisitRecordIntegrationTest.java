package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitPhotoRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import com.example.beyond_may_be.visit.service.VisitPhotoStorage;
import com.example.beyond_may_be.visit.service.VisitService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = BeyondMayBeApplication.class)
class VisitRecordIntegrationTest {
  @Autowired private VisitService service;
  @Autowired private VisitRepository visits;
  @Autowired private VisitPhotoRepository photos;
  @Autowired private ExplorationRepository explorations;
  @Autowired private ExplorationParticipantRepository participants;
  @MockitoBean private VisitPhotoStorage storage;

  @Test
  @DisplayName("완료 후 기록을 저장하고 다중 업로드 실패는 메모와 사진을 함께 롤백한다.")
  void completedVisitRecord_persistsAndRollsBackTogether() {
    Visit visit = createCompletedVisit(990001L);
    long visitId = visit.getId();
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isNull();
    String originalMemo = "가".repeat(2000);
    service.saveRecord(visitId, originalMemo, List.of(), 990001L);
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEqualTo(originalMemo);

    var file =
        new MockMultipartFile(
            "files",
            "visit.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    given(storage.createSignedGetUrl(anyString()))
        .willReturn(
            new VisitPhotoStorage.SignedUrl(
                "https://example.com/photo", Instant.now().plusSeconds(3600)));
    doNothing()
        .doThrow(new IllegalStateException("두 번째 업로드 실패"))
        .when(storage)
        .upload(anyString(), anyString(), any());
    assertThatThrownBy(() -> service.saveRecord(visitId, "실패한 메모", List.of(file, file), 990001L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("두 번째 업로드 실패");
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEqualTo(originalMemo);
    assertThat(photos.countByVisitId(visitId)).isZero();
    verify(storage, times(2)).delete(anyString());

    reset(storage);
    given(storage.createSignedGetUrl(anyString()))
        .willReturn(
            new VisitPhotoStorage.SignedUrl(
                "https://example.com/photo", Instant.now().plusSeconds(3600)));
    var result = service.saveRecord(visitId, "사진과 메모", List.of(file, file, file), 990001L);
    assertThat(result.memo()).isEqualTo("사진과 메모");
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEqualTo("사진과 메모");
    assertThat(result.photos())
        .extracting(VisitDtos.PhotoResponse::displayOrder)
        .containsExactly(1, 2, 3);
    assertThat(photos.countByVisitId(visitId)).isEqualTo(3);
    assertThatThrownBy(() -> service.saveRecord(visitId, "초과한 메모", List.of(file), 990001L))
        .isInstanceOf(VisitHandler.class);
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEqualTo("사진과 메모");
    verify(storage, times(3)).upload(anyString(), anyString(), any());

    service.saveRecord(visitId, "", List.of(), 990001L);
    assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEmpty();
    assertThat(photos.countByVisitId(visitId)).isEqualTo(3);
    assertThat(visits.findById(visitId).orElseThrow().getVisitedAt())
        .isEqualTo(visit.getVisitedAt());
  }

  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  @DisplayName("다른 저장의 잠금 해제 후 최신 메모를 읽어 빈 메모로 지운다.")
  void concurrentMemoSave_readsCommittedMemoAfterLock() throws Exception {
    Visit visit = createCompletedVisit(990002L);
    long visitId = visit.getId();
    service.saveRecord(visitId, "", List.of(), 990001L);
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var pending =
          transaction.execute(
              status -> {
                Long explorationId =
                    participants
                        .findExplorationIdByIdAndUserId(visit.getParticipantId(), 990001L)
                        .orElseThrow();
                explorations.findByIdForUpdate(explorationId).orElseThrow();
                visits.findById(visitId).orElseThrow().updateMemo("먼저 저장한 메모");
                var next =
                    executor.submit(() -> service.saveRecord(visitId, "", List.of(), 990001L));
                long deadline =
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                  jdbc.execute("SELECT pg_stat_clear_snapshot()");
                  waiting =
                      jdbc.queryForObject(
                          "SELECT count(*) > 0 FROM pg_stat_activity "
                              + "WHERE datname = current_database() AND wait_event_type = 'Lock' "
                              + "AND query LIKE '%explorations%'",
                          Boolean.class);
                  if (waiting) break;
                  try {
                    Thread.sleep(20);
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("잠금 대기 확인이 중단되었습니다.", exception);
                  }
                }
                assertThat(waiting).as("두 번째 저장이 탐험 잠금을 기다림").isTrue();
                return next;
              });
      assertThat(pending.get(15, java.util.concurrent.TimeUnit.SECONDS).memo()).isEmpty();
      assertThat(visits.findById(visitId).orElseThrow().getMemo()).isEmpty();
    }
  }

  private Visit createCompletedVisit(long courseId) {
    var exploration =
        explorations.saveAndFlush(
            Exploration.builder().courseId(courseId).status(ExplorationStatus.COMPLETED).build());
    var participant =
        participants.saveAndFlush(
            ExplorationParticipant.builder()
                .explorationId(exploration.getId())
                .userId(990001L)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.COMPLETED)
                .displayName("기록테스트")
                .joinedAt(LocalDateTime.now())
                .build());
    var visit =
        visits.saveAndFlush(
            Visit.builder()
                .participantId(participant.getId())
                .placeId(990001L)
                .visitedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                .build());
    return visit;
  }
}
