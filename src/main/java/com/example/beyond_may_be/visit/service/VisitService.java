package com.example.beyond_may_be.visit.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.visit.converter.VisitConverter;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher applicationEventPublisher;

  public VisitDtos.ConfirmResponse confirmVisit(VisitDtos.ConfirmRequest request, Long userId) {
    // ponytail: 탐험별 방문을 직렬화한다. 탐험당 처리량이 문제가 되면 장소 단위 잠금으로 좁힌다.
    Exploration exploration =
        explorationRepository
            .findByIdForUpdate(request.explorationId())
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    if (exploration.getStatus() != ExplorationStatus.ONGOING) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_ONGOING);
    }

    ExplorationParticipant participant =
        explorationParticipantRepository
            .findByExplorationIdAndUserId(request.explorationId(), userId)
            .filter(candidate -> candidate.getStatus() == ParticipantStatus.ACTIVE)
            .orElseThrow(
                () -> new ExplorationHandler(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN));
    Place place =
        placeRepository
            .findByIdAndActiveTrue(request.placeId())
            .orElseThrow(() -> new VisitHandler(ErrorStatus.PLACE_DETAIL_NOT_FOUND));

    double distanceMeters =
        GeoDistanceCalculator.distanceMeters(
            request.latitude().doubleValue(),
            request.longitude().doubleValue(),
            place.getLatitude().doubleValue(),
            place.getLongitude().doubleValue());
    if (distanceMeters > VISIT_RADIUS_METERS) {
      throw new VisitHandler(ErrorStatus.VISIT_OUT_OF_RANGE);
    }
    if (visitRepository.existsByParticipantIdAndPlaceId(participant.getId(), request.placeId())) {
      throw new VisitHandler(ErrorStatus.VISIT_ALREADY_EXISTS);
    }

    Long coursePlaceId =
        coursePlaceRepository
            .findByCourseIdAndPlaceId(exploration.getCourseId(), request.placeId())
            .map(CoursePlace::getId)
            .orElse(null);
    List<ExplorationParticipant> participants =
        explorationParticipantRepository.findByExplorationId(request.explorationId());
    List<Long> participantIds = participants.stream().map(ExplorationParticipant::getId).toList();
    boolean teamFirstVisit =
        !visitRepository.existsByPlaceIdAndParticipantIdIn(request.placeId(), participantIds);
    Visit visit =
        visitRepository.save(
            Visit.builder()
                .participantId(participant.getId())
                .placeId(request.placeId())
                .coursePlaceId(coursePlaceId)
                .visitedAt(LocalDateTime.now())
                .build());

    long completedCoursePlaceCount = visitRepository.countDistinctCoursePlaceIds(participantIds);
    long totalCoursePlaceCount = coursePlaceRepository.countByCourseId(exploration.getCourseId());
    if (totalCoursePlaceCount > 0 && completedCoursePlaceCount >= totalCoursePlaceCount) {
      exploration.complete(LocalDateTime.now());
      participants.forEach(ExplorationParticipant::complete);
    }
    VisitDtos.ConfirmResponse response =
        VisitConverter.toConfirmResponse(
            visit,
            exploration.getId(),
            Math.round(distanceMeters),
            teamFirstVisit,
            completedCoursePlaceCount,
            totalCoursePlaceCount,
            exploration.getStatus().name());
    applicationEventPublisher.publishEvent(
        VisitConverter.toVisitConfirmedEvent(
            UUID.randomUUID(), response, participant.getDisplayName()));
    if (exploration.getStatus() == ExplorationStatus.COMPLETED) {
      applicationEventPublisher.publishEvent(
          VisitConverter.toExplorationCompletedEvent(
              UUID.randomUUID(), exploration.getId(), exploration.getCompletedAt()));
    }
    return response;
  }
}
