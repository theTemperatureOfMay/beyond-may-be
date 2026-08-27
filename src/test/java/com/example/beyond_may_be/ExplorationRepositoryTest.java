package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ExplorationRepositoryTest {

  @Autowired private ExplorationRepository explorationRepository;

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
}
