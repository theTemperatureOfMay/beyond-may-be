package com.example.beyond_may_be.exploration.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.beyond_may_be.exploration.dto.SocketEventDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExplorationBroadcastServiceTest {

  @InjectMocks private ExplorationBroadcastService explorationBroadcastService;

  @Mock private SocketIOServer socketIOServer;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private VisitRepository visitRepository;
  @Mock private BroadcastOperations broadcastOperations;

  @DisplayName("visit:confirmed는 exploration:{id} room에 밀리초 단위 visitedAt으로 전파된다.")
  @Test
  void broadcastVisitConfirmed_sendsToExplorationRoom() {
    given(socketIOServer.getRoomOperations("exploration:5")).willReturn(broadcastOperations);
    LocalDateTime visitedAt = LocalDateTime.of(2026, 8, 12, 12, 0, 0);

    explorationBroadcastService.broadcastVisitConfirmed(5L, 100L, 2L, "여행자", visitedAt);

    ArgumentCaptor<SocketEventDtos.VisitConfirmedPayload> captor =
        ArgumentCaptor.forClass(SocketEventDtos.VisitConfirmedPayload.class);
    verify(broadcastOperations).sendEvent(eq("visit:confirmed"), captor.capture());
    SocketEventDtos.VisitConfirmedPayload payload = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(payload.placeId()).isEqualTo(100L);
    org.assertj.core.api.Assertions.assertThat(payload.userId()).isEqualTo(2L);
    org.assertj.core.api.Assertions.assertThat(payload.displayName()).isEqualTo("여행자");
    org.assertj.core.api.Assertions.assertThat(payload.visitedAt())
        .isEqualTo(visitedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
  }

  @DisplayName("member:location은 옵트인 사용자의 좌표를 그대로 room에 전파한다.")
  @Test
  void broadcastMemberLocation_sendsToExplorationRoom() {
    given(socketIOServer.getRoomOperations("exploration:5")).willReturn(broadcastOperations);

    explorationBroadcastService.broadcastMemberLocation(
        5L, 2L, BigDecimal.valueOf(35.15), BigDecimal.valueOf(126.85));

    verify(broadcastOperations)
        .sendEvent(
            eq("member:location"),
            eq(
                new SocketEventDtos.MemberLocationPayload(
                    2L, BigDecimal.valueOf(35.15), BigDecimal.valueOf(126.85))));
  }
}
