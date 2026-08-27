package com.example.beyond_may_be.exploration.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExplorationConverterTest {

  @DisplayName("런타임 기준으로 저장된 참여 시각을 서울 시간대로 변환한다.")
  @Test
  void toParticipantsResponse_convertsStoredRuntimeTimeToSeoul() {
    TimeZone originalTimeZone = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      ExplorationParticipant participant =
          ExplorationParticipant.builder()
              .explorationId(44L)
              .userId(8L)
              .role(ParticipantRole.MEMBER)
              .status(ParticipantStatus.ACTIVE)
              .displayName("별밤지기")
              .locationSharingEnabled(false)
              .joinedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
              .build();
      ReflectionTestUtils.setField(participant, "id", 71L);

      ExplorationDtos.ParticipantsResponse response =
          ExplorationConverter.toParticipantsResponse(
              44L, List.of(participant), Map.of(), participant.getId());

      assertThat(response.participants().getFirst().joinedAt())
          .isEqualTo(OffsetDateTime.parse("2026-08-15T18:00:00+09:00"));
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }
}
