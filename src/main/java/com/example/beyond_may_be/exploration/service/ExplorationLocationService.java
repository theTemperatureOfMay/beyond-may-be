package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.converter.ExplorationConverter;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Service
@Transactional(readOnly = true)
public class ExplorationLocationService {

  private static final BigDecimal MAX_ACCURACY_METERS = new BigDecimal("50");
  private static final double MIN_MOVEMENT_METERS = 10.0;
  private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final SimpMessagingTemplate messagingTemplate;
  private final Map<String, AcceptedLocation> lastAcceptedLocations = new ConcurrentHashMap<>();

  public ExplorationLocationService(
      ExplorationRepository explorationRepository,
      ExplorationParticipantRepository explorationParticipantRepository,
      @Lazy SimpMessagingTemplate messagingTemplate) {
    this.explorationRepository = explorationRepository;
    this.explorationParticipantRepository = explorationParticipantRepository;
    this.messagingTemplate = messagingTemplate;
  }

  @Transactional
  public void accept(
      String sessionId,
      Long explorationId,
      Long userId,
      ExplorationDtos.LocationUpdateRequest request) {
    // ponytail: 기존 쓰기 잠금을 재사용해 상태 변경과 직렬화한다. 위치 처리량이 병목이면 공유 잠금으로 좁힌다.
    requireOngoingExploration(explorationRepository.findByIdForUpdate(explorationId));
    ExplorationParticipant participant =
        requireActiveParticipant(
            explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(
                explorationId, userId));
    if (!participant.isLocationSharingEnabled()) {
      throw new StompContractException(Code.LOCATION_SHARING_DISABLED);
    }
    if (request.accuracyMeters().compareTo(MAX_ACCURACY_METERS) > 0) {
      throw new StompContractException(Code.LOCATION_ACCURACY_EXCEEDED);
    }
    AcceptedLocation current =
        new AcceptedLocation(
            explorationId, participant.getId(), request.latitude(), request.longitude());
    AtomicBoolean accepted = new AtomicBoolean();
    lastAcceptedLocations.compute(
        sessionId,
        (ignored, previous) -> {
          if (previous == null
              || !previous.sameParticipant(current)
              || previous.distanceMetersTo(current) >= MIN_MOVEMENT_METERS) {
            accepted.set(true);
            return current;
          }
          return previous;
        });
    if (!accepted.get()) {
      return;
    }

    ExplorationDtos.LocationUpdatedEvent event =
        ExplorationConverter.toLocationUpdatedEvent(
            UUID.randomUUID(), OffsetDateTime.now(EVENT_ZONE), participant, request);
    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
    headers.setLeaveMutable(true);
    messagingTemplate.convertAndSend(
        "/topic/explorations/" + explorationId + "/locations", event, headers.getMessageHeaders());
  }

  public void validateSubscription(Long explorationId, Long userId) {
    requireOngoingExploration(explorationRepository.findById(explorationId));
    requireActiveParticipant(
        explorationParticipantRepository.findByExplorationIdAndUserId(explorationId, userId));
  }

  @EventListener
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void handleDisconnect(SessionDisconnectEvent event) {
    lastAcceptedLocations.remove(event.getSessionId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void handleLocationSharingChanged(ExplorationDtos.LocationSharingChangedEvent event) {
    lastAcceptedLocations.forEach(
        (sessionId, location) -> {
          if (location.belongsTo(event.explorationId(), event.data().participantId())) {
            lastAcceptedLocations.remove(sessionId, location);
          }
        });
  }

  private void requireOngoingExploration(Optional<Exploration> result) {
    Exploration exploration =
        result.orElseThrow(() -> new StompContractException(Code.EXPLORATION_NOT_ONGOING));
    if (exploration.getStatus() != ExplorationStatus.ONGOING) {
      throw new StompContractException(Code.EXPLORATION_NOT_ONGOING);
    }
  }

  private ExplorationParticipant requireActiveParticipant(Optional<ExplorationParticipant> result) {
    return result
        .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
        .orElseThrow(() -> new StompContractException(Code.PARTICIPANT_NOT_ACTIVE));
  }

  private record AcceptedLocation(
      Long explorationId, Long participantId, BigDecimal latitude, BigDecimal longitude) {

    private boolean sameParticipant(AcceptedLocation other) {
      return explorationId.equals(other.explorationId) && participantId.equals(other.participantId);
    }

    private boolean belongsTo(Long explorationId, Long participantId) {
      return this.explorationId.equals(explorationId) && this.participantId.equals(participantId);
    }

    private double distanceMetersTo(AcceptedLocation other) {
      return GeoDistanceCalculator.distanceMeters(
          latitude.doubleValue(),
          longitude.doubleValue(),
          other.latitude.doubleValue(),
          other.longitude.doubleValue());
    }
  }
}
