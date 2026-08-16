package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.converter.ExplorationConverter;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExplorationService {

  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final CourseRepository courseRepository;
  private final UserRepository userRepository;
  private final VisitRepository visitRepository;
  private final ExplorationBroadcastService explorationBroadcastService;

  public ExplorationDtos.JoinResponse join(Long courseId, Long userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_NOT_FOUND));
    if (course.getStatus() != CourseStatus.CONFIRMED) {
      throw new CourseHandler(ErrorStatus.COURSE_NOT_FOUND);
    }
    if (course.getShareExpiresAt() == null
        || LocalDateTime.now().isAfter(course.getShareExpiresAt())) {
      throw new ExplorationHandler(ErrorStatus.SHARE_LINK_EXPIRED);
    }

    Exploration exploration =
        explorationRepository
            .findByCourseId(courseId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));

    var existing =
        explorationParticipantRepository.findByExplorationIdAndUserId(exploration.getId(), userId);
    if (existing.isPresent()) {
      ExplorationParticipant participant = existing.get();
      if (participant.getStatus() == ParticipantStatus.LEFT) {
        participant.reactivate();
      }
      explorationBroadcastService.broadcastMemberJoined(
          exploration.getId(), participant.getUserId(), participant.getDisplayName());
      return ExplorationConverter.toJoinResponse(participant);
    }

    if (explorationParticipantRepository.existsActiveParticipationElsewhere(
        userId, exploration.getId())) {
      throw new ExplorationHandler(ErrorStatus.DUPLICATE_ACTIVE_PARTICIPATION);
    }

    User user =
        userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    String displayName = resolveDisplayName(exploration.getId(), user.getNickname());

    ExplorationParticipant participant =
        explorationParticipantRepository.save(
            ExplorationParticipant.builder()
                .explorationId(exploration.getId())
                .userId(userId)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .displayName(displayName)
                .locationSharingEnabled(false)
                .joinedAt(LocalDateTime.now())
                .build());

    explorationBroadcastService.broadcastMemberJoined(exploration.getId(), userId, displayName);
    return ExplorationConverter.toJoinResponse(participant);
  }

  private String resolveDisplayName(Long explorationId, String nickname) {
    List<ExplorationParticipant> allParticipants =
        explorationParticipantRepository.findByExplorationId(explorationId);
    Pattern suffixPattern = Pattern.compile(Pattern.quote(nickname) + " \\((\\d+)\\)$");
    long sameNameCount =
        allParticipants.stream()
            .filter(
                participant ->
                    participant.getDisplayName().equals(nickname)
                        || suffixPattern.matcher(participant.getDisplayName()).matches())
            .count();
    return sameNameCount == 0 ? nickname : nickname + " (" + (sameNameCount + 1) + ")";
  }

  @Transactional(readOnly = true)
  public ExplorationDtos.MemberListResponse listMembers(Long explorationId, Long userId) {
    requireActiveParticipant(explorationId, userId);
    List<ExplorationParticipant> members =
        explorationParticipantRepository.findByExplorationIdAndStatus(
            explorationId, ParticipantStatus.ACTIVE);

    List<Long> participantIds = members.stream().map(ExplorationParticipant::getId).toList();
    Map<Long, Integer> visitedCountsByParticipantId = new HashMap<>();
    for (VisitRepository.ParticipantVisitCount count :
        visitRepository.countByParticipantIds(participantIds)) {
      visitedCountsByParticipantId.put(count.getParticipantId(), (int) count.getVisitCount());
    }

    return ExplorationConverter.toMemberListResponse(members, visitedCountsByParticipantId);
  }

  public ExplorationDtos.StartResponse start(Long explorationId, Long userId) {
    ExplorationParticipant participant = requireActiveParticipant(explorationId, userId);

    int updated =
        explorationRepository.startIfBefore(explorationId, participant.getId(), LocalDateTime.now());
    if (updated == 0) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_ALREADY_STARTED);
    }

    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    return ExplorationConverter.toStartResponse(exploration);
  }

  private ExplorationParticipant requireActiveParticipant(Long explorationId, Long userId) {
    return explorationParticipantRepository
        .findByExplorationIdAndUserId(explorationId, userId)
        .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
        .orElseThrow(() -> new ExplorationHandler(ErrorStatus.PARTICIPANT_NOT_ACTIVE));
  }

  @Transactional(readOnly = true)
  public boolean isActiveAndLocationSharing(Long explorationId, Long userId) {
    return explorationParticipantRepository
        .findByExplorationIdAndUserId(explorationId, userId)
        .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
        .map(ExplorationParticipant::isLocationSharingEnabled)
        .orElse(false);
  }

  public void setLocationSharing(Long explorationId, Long userId, boolean enabled) {
    explorationParticipantRepository
        .findByExplorationIdAndUserId(explorationId, userId)
        .ifPresent(participant -> participant.setLocationSharing(enabled));
  }
}
