package com.example.beyond_may_be.visit.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.converter.ExplorationConverter;
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
import com.example.beyond_may_be.visit.domain.VisitPhoto;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitPhotoRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VisitService {

  private static final double VISIT_RADIUS_METERS = 100;
  private static final long MAX_PHOTO_SIZE_BYTES = 10L * 1024 * 1024;
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };
  private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
  private static final byte[] WEBP_SIGNATURE = {0x57, 0x45, 0x42, 0x50};

  private final VisitRepository visitRepository;
  private final PlaceRepository placeRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final VisitPhotoRepository visitPhotoRepository;
  private final VisitPhotoStorage visitPhotoStorage;

  @Transactional(readOnly = true)
  public VisitDtos.VisitsResponse getVisits(Long explorationId, Long userId) {
    explorationRepository
        .findById(explorationId)
        .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    explorationParticipantRepository
        .findByExplorationIdAndUserId(explorationId, userId)
        .orElseThrow(() -> new ExplorationHandler(ErrorStatus._FORBIDDEN));

    List<ExplorationParticipant> participants =
        explorationParticipantRepository.findByExplorationId(explorationId);
    Map<Long, ExplorationParticipant> participantsById =
        participants.stream()
            .collect(Collectors.toMap(ExplorationParticipant::getId, Function.identity()));
    List<Visit> visits =
        visitRepository.findByParticipantIdInOrderByVisitedAtDesc(
            participants.stream().map(ExplorationParticipant::getId).toList());
    if (visits.isEmpty()) {
      return VisitConverter.toVisitsResponse(explorationId, List.of());
    }

    Map<Long, Place> placesById =
        placeRepository
            .findAllById(visits.stream().map(Visit::getPlaceId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Place::getId, Function.identity()));
    List<Long> visitIds = visits.stream().map(Visit::getId).toList();
    Map<Long, List<VisitPhoto>> photosByVisitId =
        visitPhotoRepository.findByVisitIdInOrderByVisitIdAscDisplayOrderAsc(visitIds).stream()
            .collect(Collectors.groupingBy(VisitPhoto::getVisitId));

    List<VisitDtos.VisitResponse> responses =
        visits.stream()
            .map(
                visit -> {
                  List<VisitDtos.VisitPhotoResponse> photos =
                      photosByVisitId.getOrDefault(visit.getId(), List.of()).stream()
                          .map(
                              photo -> {
                                VisitPhotoStorage.SignedUrl signedUrl =
                                    visitPhotoStorage.createSignedGetUrl(photo.getObjectKey());
                                return VisitConverter.toVisitPhotoResponse(
                                    photo, signedUrl.imageUrl(), signedUrl.expiresAt());
                              })
                          .toList();
                  return VisitConverter.toVisitResponse(
                      visit,
                      Objects.requireNonNull(
                          participantsById.get(visit.getParticipantId()), "방문 참여자 정보가 없습니다."),
                      Objects.requireNonNull(placesById.get(visit.getPlaceId()), "방문 장소 정보가 없습니다."),
                      photos);
                })
            .toList();
    return VisitConverter.toVisitsResponse(explorationId, responses);
  }

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
          ExplorationConverter.toExplorationCompletedEvent(
              UUID.randomUUID(),
              exploration.getId(),
              exploration.getCompletedAt(),
              "ALL_COURSE_PLACES_VISITED"));
    }
    return response;
  }

  public VisitDtos.PhotoResponse attachPhoto(Long visitId, MultipartFile file, Long userId) {
    String contentType = validatePhoto(file);
    Visit visit =
        visitRepository
            .findById(visitId)
            .orElseThrow(() -> new VisitHandler(ErrorStatus.VISIT_NOT_FOUND));
    ExplorationParticipant participant =
        explorationParticipantRepository
            .findById(visit.getParticipantId())
            .orElseThrow(() -> new IllegalStateException("방문 참여자 정보가 없습니다."));
    if (!participant.getUserId().equals(userId)) {
      throw new ExplorationHandler(ErrorStatus._FORBIDDEN);
    }

    Exploration exploration =
        explorationRepository
            .findByIdForUpdate(participant.getExplorationId())
            .orElseThrow(() -> new IllegalStateException("방문 탐험 정보가 없습니다."));
    if (exploration.getStatus() == ExplorationStatus.COMPLETED) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
    }
    if (participant.getStatus() != ParticipantStatus.ACTIVE) {
      throw new ExplorationHandler(ErrorStatus._FORBIDDEN);
    }
    visitRepository
        .findByIdForUpdate(visitId)
        .orElseThrow(() -> new VisitHandler(ErrorStatus.VISIT_NOT_FOUND));

    int displayOrder = visitPhotoRepository.findMaxDisplayOrderByVisitId(visitId) + 1;
    String objectKey = "visits/" + visitId + "/" + UUID.randomUUID();
    boolean rollbackCleanupRegistered = false;
    try {
      visitPhotoStorage.upload(objectKey, contentType, file);
      rollbackCleanupRegistered = registerRollbackCleanup(objectKey);
      VisitPhoto photo =
          visitPhotoRepository.saveAndFlush(
              VisitPhoto.builder()
                  .visitId(visitId)
                  .objectKey(objectKey)
                  .displayOrder(displayOrder)
                  .build());
      VisitPhotoStorage.SignedUrl signedUrl = visitPhotoStorage.createSignedGetUrl(objectKey);
      return VisitConverter.toPhotoResponse(photo, signedUrl.imageUrl(), signedUrl.expiresAt());
    } catch (DataIntegrityViolationException exception) {
      if (!rollbackCleanupRegistered) {
        deleteUploadedObject(objectKey);
      }
      throw new VisitHandler(ErrorStatus.VISIT_PHOTO_ORDER_CONFLICT);
    } catch (RuntimeException exception) {
      if (!rollbackCleanupRegistered) {
        deleteUploadedObject(objectKey);
      }
      throw exception;
    }
  }

  private boolean registerRollbackCleanup(String objectKey) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return false;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              deleteUploadedObject(objectKey);
            }
          }
        });
    return true;
  }

  private void deleteUploadedObject(String objectKey) {
    try {
      visitPhotoStorage.delete(objectKey);
    } catch (RuntimeException cleanupException) {
      log.error("방문 사진 객체 삭제에 실패했습니다. objectKey={}", objectKey, cleanupException);
    }
  }

  private String validatePhoto(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new VisitHandler(ErrorStatus._BAD_REQUEST);
    }
    if (file.getSize() > MAX_PHOTO_SIZE_BYTES) {
      throw new VisitHandler(ErrorStatus.VISIT_PHOTO_TOO_LARGE);
    }

    byte[] header;
    try (var inputStream = file.getInputStream()) {
      header = inputStream.readNBytes(12);
    } catch (IOException exception) {
      throw new IllegalStateException("방문 사진 형식을 확인할 수 없습니다.", exception);
    }

    String detectedContentType =
        matches(header, 0, JPEG_SIGNATURE)
            ? "image/jpeg"
            : matches(header, 0, PNG_SIGNATURE)
                ? "image/png"
                : matches(header, 0, RIFF_SIGNATURE) && matches(header, 8, WEBP_SIGNATURE)
                    ? "image/webp"
                    : null;
    if (!java.util.Objects.equals(detectedContentType, file.getContentType())) {
      throw new VisitHandler(ErrorStatus.VISIT_PHOTO_UNSUPPORTED_TYPE);
    }
    return detectedContentType;
  }

  private boolean matches(byte[] value, int offset, byte[] expected) {
    if (value.length < offset + expected.length) {
      return false;
    }
    for (int index = 0; index < expected.length; index++) {
      if (value[offset + index] != expected[index]) {
        return false;
      }
    }
    return true;
  }
}
