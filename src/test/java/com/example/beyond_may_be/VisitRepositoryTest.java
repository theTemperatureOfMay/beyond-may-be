package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VisitRepositoryTest {

  @Autowired private VisitRepository visitRepository;

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

  private Visit visit(long participantId, long placeId, Long coursePlaceId) {
    return Visit.builder()
        .participantId(participantId)
        .placeId(placeId)
        .coursePlaceId(coursePlaceId)
        .visitedAt(LocalDateTime.of(2026, 8, 15, 11, 0))
        .build();
  }
}
