package com.example.beyond_may_be.visit.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.exploration.service.ExplorationBroadcastService;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.visit.converter.VisitConverter;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitService {

  private static final double VISIT_RADIUS_METERS = 100;

  private final VisitRepository visitRepository;
  private final PlaceRepository placeRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final ExplorationBroadcastService explorationBroadcastService;

  public VisitDtos.ConfirmResponse confirmVisit(
      Long explorationId, Long userId, Long placeId, BigDecimal latitude, BigDecimal longitude) {
    ExplorationParticipant participant =
        explorationParticipantRepository
            .findByExplorationIdAndUserId(explorationId, userId)
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
            .orElseThrow(() -> new VisitHandler(ErrorStatus.PARTICIPANT_NOT_ACTIVE));

    Place place =
        placeRepository.findById(placeId).orElseThrow(() -> new VisitHandler(ErrorStatus.PLACE_NOT_FOUND));

    double distance =
        GeoDistanceCalculator.distanceMeters(
            latitude.doubleValue(),
            longitude.doubleValue(),
            place.getLatitude().doubleValue(),
            place.getLongitude().doubleValue());
    if (distance > VISIT_RADIUS_METERS) {
      throw new VisitHandler(ErrorStatus.VISIT_OUT_OF_RANGE);
    }

    if (visitRepository.existsByParticipantIdAndPlaceId(participant.getId(), placeId)) {
      throw new VisitHandler(ErrorStatus.VISIT_ALREADY_EXISTS);
    }

    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));

    Long coursePlaceId =
        coursePlaceRepository
            .findByCourseIdAndPlaceId(exploration.getCourseId(), placeId)
            .map(CoursePlace::getId)
            .orElse(null);

    List<Long> participantIds =
        explorationParticipantRepository.findParticipantIdsByExplorationId(explorationId);

    boolean teamFirstVisit =
        coursePlaceId != null
            && !visitRepository.existsByCoursePlaceIdAndParticipantIdIn(
                coursePlaceId, participantIds);

    Visit visit =
        visitRepository.save(
            Visit.builder()
                .participantId(participant.getId())
                .placeId(placeId)
                .coursePlaceId(coursePlaceId)
                .visitedAt(LocalDateTime.now())
                .build());

    boolean explorationCompleted = false;
    if (teamFirstVisit) {
      long totalCoursePlaces = coursePlaceRepository.countByCourseId(exploration.getCourseId());
      long completedCoursePlaces = visitRepository.countDistinctCoursePlaceIds(participantIds);
      if (totalCoursePlaces > 0 && completedCoursePlaces >= totalCoursePlaces) {
        int updated = explorationRepository.completeIfOngoing(explorationId, LocalDateTime.now());
        explorationCompleted = updated == 1;
      }
    }

    int visitedCount =
        visitRepository.countByParticipantIds(List.of(participant.getId())).stream()
            .findFirst()
            .map(count -> (int) count.getVisitCount())
            .orElse(0);
    explorationBroadcastService.broadcastVisitConfirmed(
        explorationId, placeId, userId, participant.getDisplayName(), visit.getVisitedAt());
    explorationBroadcastService.broadcastMemberProgress(
        explorationId, userId, participant.getDisplayName(), visitedCount);

    return VisitConverter.toConfirmResponse(visit, teamFirstVisit, explorationCompleted);
  }
}
