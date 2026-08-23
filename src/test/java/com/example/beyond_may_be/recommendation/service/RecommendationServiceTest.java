package com.example.beyond_may_be.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.RecommendationHandler;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.place.service.PlaceDetailEnrichmentService;
import com.example.beyond_may_be.place.service.TourApiSyncClient;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.recommendation.dto.RecommendationDtos;
import com.example.beyond_may_be.recommendation.repository.RecommendationSetRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @InjectMocks private RecommendationService recommendationService;

  @Mock private UserRepository userRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private RecommendationSetRepository recommendationSetRepository;
  @Mock private TourApiSyncClient tourApiSyncClient;
  @Mock private PlaceDetailEnrichmentService placeDetailEnrichmentService;
  @Mock private GroqRecommendationClient groqRecommendationClient;
  @Mock private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUpTransactions() {
    lenient()
        .when(userRepository.findById(any()))
        .thenAnswer(invocation -> userRepository.findByIdForUpdate(invocation.getArgument(0)));
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
  }

  @Test
  void replacesAllReactionsForTheRequestedBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    RecommendationSet stored =
        recommendationSet(
            12L, TravelSchedule.DAY_TRIP, List.of(1L, 2L, 3L), List.of(1L), List.of(2L, 3L));
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L, 2L, 3L), List.of()));

    assertThat(stored.getLikedPlaceIds()).containsExactly(1L, 2L, 3L);
    assertThat(stored.getDislikedPlaceIds()).isEmpty();
    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(response.batchNumber()).isEqualTo(1);
    assertThat(response.selectedPlaceCount()).isEqualTo(3);
    assertThat(response.minimumSelectionCount()).isEqualTo(3);
    assertThat(response.selectionReady()).isTrue();
    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
  }

  @Test
  void getsCurrentRecommendationWithPersistedBatchProgress() {
    List<Long> recommendedPlaceIds =
        java.util.stream.LongStream.rangeClosed(1, 25).boxed().toList();
    List<Long> dislikedPlaceIds =
        java.util.stream.LongStream.rangeClosed(2, 20)
            .filter(placeId -> placeId != 3)
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));
    dislikedPlaceIds.add(22L);
    RecommendationSet stored =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            recommendedPlaceIds,
            List.of(1L, 3L, 21L),
            dislikedPlaceIds);
    List<Place> places = places(7).subList(0, 25);
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(recommendedPlaceIds)).willReturn(places);

    RecommendationDtos.CurrentRecommendationResponse response =
        recommendationService.getCurrent(1L);

    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(response.travelSchedule()).isEqualTo(TravelSchedule.DAY_TRIP);
    assertThat(response.batchSize()).isEqualTo(20);
    assertThat(response.minimumSelectionCount()).isEqualTo(3);
    assertThat(response.selectedPlaceCount()).isEqualTo(3);
    assertThat(response.selectionReady()).isTrue();
    assertThat(response.batches()).hasSize(2);
    assertThat(response.batches().getFirst().places()).hasSize(20);
    assertThat(response.batches().getFirst().likedPlaceIds()).containsExactly(1L, 3L);
    assertThat(response.batches().getFirst().dislikedPlaceIds()).hasSize(18);
    assertThat(response.batches().getFirst().completed()).isTrue();
    assertThat(response.batches().get(1).places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactly(21L, 22L, 23L, 24L, 25L);
    assertThat(response.batches().get(1).likedPlaceIds()).containsExactly(21L);
    assertThat(response.batches().get(1).dislikedPlaceIds()).containsExactly(22L);
    assertThat(response.batches().get(1).completed()).isFalse();
  }

  @Test
  void omitsUnavailablePlacesFromCurrentRecommendationProgress() {
    RecommendationSet stored =
        recommendationSet(
            12L, TravelSchedule.DAY_TRIP, List.of(1L, 2L, 3L), List.of(1L, 2L), List.of(3L));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(List.of(1L, 2L, 3L)))
        .willReturn(
            List.of(
                place(1L, TravelPreferenceType.THINKER, true),
                place(2L, TravelPreferenceType.FOODIE, false)));

    RecommendationDtos.CurrentRecommendationResponse response =
        recommendationService.getCurrent(1L);

    assertThat(response.selectedPlaceCount()).isEqualTo(2);
    assertThat(response.batches())
        .singleElement()
        .satisfies(
            batch -> {
              assertThat(batch.places())
                  .extracting(RecommendationDtos.PlaceResponse::placeId)
                  .containsExactly(1L);
              assertThat(batch.likedPlaceIds()).containsExactly(1L);
              assertThat(batch.dislikedPlaceIds()).isEmpty();
              assertThat(batch.completed()).isTrue();
            });
  }

  @Test
  void returnsTerminalEmptyBatchForCurrentRecommendationWithoutPlaces() {
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, List.of(), List.of(), List.of());
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    RecommendationDtos.CurrentRecommendationResponse response =
        recommendationService.getCurrent(1L);

    assertThat(response.batches())
        .singleElement()
        .satisfies(
            batch -> {
              assertThat(batch.batchNumber()).isEqualTo(1);
              assertThat(batch.places()).isEmpty();
              assertThat(batch.completed()).isTrue();
            });
  }

  @Test
  void reportsMissingCurrentRecommendationForLookup() {
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> recommendationService.getCurrent(1L))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_NOT_FOUND);
  }

  @ParameterizedTest
  @MethodSource("invalidReactionRequests")
  void rejectsReactionsThatDoNotPartitionTheBatch(RecommendationDtos.ReactionRequest request) {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, List.of(1L, 2L, 3L), List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    assertThatThrownBy(() -> recommendationService.replaceBatchReactions(1L, 1, request))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_INVALID_REACTIONS);
  }

  @Test
  void acceptsReactionsForOnlyTheActivePlacesStillVisibleInTheBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    List<Long> storedBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    List<Place> visiblePlaces =
        List.of(
            place(1L, TravelPreferenceType.THINKER, true),
            place(2L, TravelPreferenceType.THINKER, true),
            place(3L, TravelPreferenceType.THINKER, true));
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, storedBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(storedBatch)).willReturn(visiblePlaces);

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L, 2L, 3L), List.of()));

    assertThat(response.selectionReady()).isTrue();
    assertThat(stored.getLikedPlaceIds()).containsExactly(1L, 2L, 3L);
    assertThat(stored.getDislikedPlaceIds()).isEmpty();
  }

  @Test
  void reportsMissingCurrentRecommendation() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recommendationService.replaceBatchReactions(
                    1L, 1, new RecommendationDtos.ReactionRequest(List.of(), List.of())))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_NOT_FOUND);
  }

  @Test
  void reportsMissingRecommendationBeforePreferenceState() {
    User user = user(null, 0, 0, 0, 0);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                recommendationService.replaceBatchReactions(
                    1L, 1, new RecommendationDtos.ReactionRequest(List.of(), List.of())))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_NOT_FOUND);
  }

  @Test
  void reportsMissingRecommendationBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, List.of(1L), List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    assertThatThrownBy(
            () ->
                recommendationService.replaceBatchReactions(
                    1L, 2, new RecommendationDtos.ReactionRequest(List.of(), List.of())))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_BATCH_NOT_FOUND);
  }

  @Test
  void rejectsNonPositiveBatchNumber() {
    assertThatThrownBy(
            () ->
                recommendationService.replaceBatchReactions(
                    1L, 0, new RecommendationDtos.ReactionRequest(List.of(), List.of())))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_INVALID_REACTIONS);
  }

  @Test
  void appendsNextBatchWithoutPreviouslyRecommendedPlaces() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(10);
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(places);

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L,
            1,
            new RecommendationDtos.ReactionRequest(
                List.of(1L), java.util.stream.LongStream.rangeClosed(2, 20).boxed().toList()));

    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isTrue();
    assertThat(response.nextBatch().batchNumber()).isEqualTo(2);
    assertThat(response.nextBatch().places()).hasSize(20);
    assertThat(response.nextBatch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .doesNotContainAnyElementsOf(firstBatch);
    assertThat(stored.getRecommendedPlaceIds()).hasSize(40);
    assertThat(stored.getLikedPlaceIds()).containsExactly(1L);
    verify(groqRecommendationClient).rank(any());
  }

  @Test
  void refillsPlacesFromTourApiBeforeCreatingTheNextBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> before =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                10,
                TravelPreferenceType.FOODIE,
                10,
                TravelPreferenceType.ARTIST,
                10,
                TravelPreferenceType.REMEMBERER,
                9));
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    Place added = place(999L, TravelPreferenceType.THINKER, true);
    List<Place> after = new ArrayList<>(before);
    after.add(added);
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(before, after, after);
    given(tourApiSyncClient.fetchChangedPlaces())
        .willReturn(
            List.of(
                syncItem(
                    "광주광역시 북구 무등로 1",
                    "(금곡동)",
                    "999001",
                    "12",
                    "",
                    "126.900001",
                    "35.200001",
                    "1",
                    "새 자연 장소",
                    "NA")));
    given(placeRepository.findExistingTourContentIds(List.of(999001L))).willReturn(Set.of());

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L,
            1,
            new RecommendationDtos.ReactionRequest(
                List.of(1L), firstBatch.subList(1, firstBatch.size())));

    assertThat(response.nextBatch().places()).hasSize(20);
    assertThat(response.nextBatch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .contains(999L)
        .doesNotContainAnyElementsOf(firstBatch);
    verify(tourApiSyncClient).fetchChangedPlaces();
  }

  @Test
  void doesNotReuseDislikedPlacesToFillAPartialNextBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> activePlaces =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                10,
                TravelPreferenceType.FOODIE,
                10,
                TravelPreferenceType.ARTIST,
                5,
                TravelPreferenceType.REMEMBERER,
                5));
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    List<Long> dislikedPlaceIds = firstBatch.subList(1, firstBatch.size());
    List<Long> newPlaceIds = java.util.stream.LongStream.rangeClosed(21, 30).boxed().toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(activePlaces);
    given(tourApiSyncClient.fetchChangedPlaces()).willReturn(List.of());

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L), dislikedPlaceIds));

    List<Long> nextPlaceIds =
        response.nextBatch().places().stream()
            .map(RecommendationDtos.PlaceResponse::placeId)
            .toList();
    assertThat(nextPlaceIds).containsExactlyInAnyOrderElementsOf(newPlaceIds);
    assertThat(nextPlaceIds).doesNotContainAnyElementsOf(dislikedPlaceIds);
    assertThat(stored.getRecommendedPlaceIds()).hasSize(30).doesNotHaveDuplicates();
  }

  @Test
  void doesNotReuseDislikedPlacesWhenNoNewPlacesRemain() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> activePlaces = places(5);
    List<Long> firstBatch = activePlaces.stream().map(Place::getId).toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(activePlaces);
    given(tourApiSyncClient.fetchChangedPlaces()).willReturn(List.of());
    RecommendationDtos.ReactionRequest request =
        new RecommendationDtos.ReactionRequest(List.of(), firstBatch);

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(1L, 1, request);

    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
    assertThat(stored.getRecommendedPlaceIds()).containsExactlyElementsOf(firstBatch);
  }

  @Test
  void appendsRemainingUnseenPlacesAsFinalPartialBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> activePlaces =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                6,
                TravelPreferenceType.FOODIE,
                5,
                TravelPreferenceType.ARTIST,
                5,
                TravelPreferenceType.REMEMBERER,
                5));
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(activePlaces);
    given(tourApiSyncClient.fetchChangedPlaces()).willReturn(List.of());

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L,
            1,
            new RecommendationDtos.ReactionRequest(
                List.of(1L, 2L), firstBatch.subList(2, firstBatch.size())));

    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isTrue();
    assertThat(response.nextBatch().batchNumber()).isEqualTo(2);
    assertThat(response.nextBatch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactly(21L);
    assertThat(stored.getRecommendedPlaceIds())
        .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList());
  }

  @Test
  void keepsAnExistingPartialBatchTerminalWhenMorePlacesBecomeAvailable() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 8).boxed().toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L,
            1,
            new RecommendationDtos.ReactionRequest(
                List.of(1L), firstBatch.subList(1, firstBatch.size())));

    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
    assertThat(stored.getRecommendedPlaceIds()).containsExactlyElementsOf(firstBatch);
    verify(tourApiSyncClient, never()).fetchChangedPlaces();
    verify(groqRecommendationClient, never()).rank(any());
  }

  @Test
  void returnsAnExistingIncompleteBatchWhenReactionsAreResubmitted() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(10);
    List<Long> recommendedPlaceIds =
        java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList();
    List<Long> firstBatch = recommendedPlaceIds.subList(0, 20);
    List<Long> secondBatch = recommendedPlaceIds.subList(20, 40);
    RecommendationSet stored =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            recommendedPlaceIds,
            List.of(1L),
            firstBatch.subList(1, 20));
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(secondBatch)).willReturn(places.subList(20, 40));

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L), firstBatch.subList(1, 20)));

    assertThat(response.nextBatch().batchNumber()).isEqualTo(2);
    assertThat(response.nextBatch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactlyElementsOf(secondBatch);
    assertThat(stored.getRecommendedPlaceIds()).hasSize(40);
    verify(groqRecommendationClient, never()).rank(any());
  }

  @Test
  void skipsAStoredPendingBatchWhenNoneOfItsPlacesRemainVisible() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Long> recommendedPlaceIds =
        java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList();
    List<Long> firstBatch = recommendedPlaceIds.subList(0, 20);
    List<Long> secondBatch = recommendedPlaceIds.subList(20, 40);
    RecommendationSet stored =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            recommendedPlaceIds,
            List.of(1L),
            firstBatch.subList(1, firstBatch.size()));
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(secondBatch)).willReturn(List.of());
    given(placeRepository.findAllByActiveTrue()).willReturn(List.of());
    given(tourApiSyncClient.fetchChangedPlaces()).willReturn(List.of());

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L,
            1,
            new RecommendationDtos.ReactionRequest(
                List.of(1L), firstBatch.subList(1, firstBatch.size())));

    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
  }

  @Test
  void resubmittingAnEarlierBatchRemovesItsPreviousLikes() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Long> recommendedPlaceIds =
        java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList();
    List<Long> firstBatch = recommendedPlaceIds.subList(0, 20);
    RecommendationSet stored =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            recommendedPlaceIds,
            List.of(1L, 2L, 3L, 21L),
            java.util.stream.Stream.concat(
                    firstBatch.subList(3, 20).stream(),
                    recommendedPlaceIds.subList(21, 40).stream())
                .toList());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(List.of());

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L), firstBatch.subList(1, 20)));

    assertThat(stored.getLikedPlaceIds()).containsExactlyInAnyOrder(1L, 21L);
    assertThat(stored.getLikedPlaceIds()).doesNotContain(2L, 3L);
    assertThat(response.selectedPlaceCount()).isEqualTo(2);
    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isFalse();
  }

  @Test
  void returnsEmptyStateWhenNoAdditionalPlacesRemain() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(5);
    List<Long> firstBatch = places.stream().map(Place::getId).toList();
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(places);

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(1L), firstBatch.subList(1, 20)));

    assertThat(response.selectedPlaceCount()).isEqualTo(1);
    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
    assertThat(stored.getLikedPlaceIds()).containsExactly(1L);
    assertThat(stored.getDislikedPlaceIds()).containsExactlyElementsOf(firstBatch.subList(1, 20));
    verify(groqRecommendationClient, never()).rank(any());
  }

  @Test
  void keepsAnEmptyInitialRecommendationAsTheTerminalEmptyState() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, List.of(), List.of(), List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));

    RecommendationDtos.ReactionResponse response =
        recommendationService.replaceBatchReactions(
            1L, 1, new RecommendationDtos.ReactionRequest(List.of(), List.of()));

    assertThat(response.batchNumber()).isEqualTo(1);
    assertThat(response.selectedPlaceCount()).isZero();
    assertThat(response.selectionReady()).isFalse();
    assertThat(response.hasNextBatch()).isFalse();
    assertThat(response.nextBatch()).isNull();
    assertThat(stored.getRecommendedPlaceIds()).isEmpty();
    verify(placeRepository, never()).findAllByActiveTrue();
    verify(tourApiSyncClient, never()).fetchChangedPlaces();
    verify(groqRecommendationClient, never()).rank(any());
  }

  @Test
  void reportsConflictWhenRecommendationCompositionChangesDuringRanking() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(10);
    List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList();
    RecommendationSet before =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, firstBatch, List.of(), List.of());
    RecommendationSet changed =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            java.util.stream.LongStream.rangeClosed(21, 40).boxed().toList(),
            List.of(),
            List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L))
        .willReturn(Optional.of(before), Optional.of(changed));
    given(placeRepository.findAllByActiveTrue()).willReturn(places);

    assertThatThrownBy(
            () ->
                recommendationService.replaceBatchReactions(
                    1L,
                    1,
                    new RecommendationDtos.ReactionRequest(List.of(1L), firstBatch.subList(1, 20))))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_BATCH_CONFLICT);

    assertThat(before.getLikedPlaceIds()).isEmpty();
  }

  @Test
  void createsTwentyPlacesInFourThreeTwoOneRatio() {
    User user =
        User.builder()
            .nickname("여행자")
            .identificationCode(1)
            .preferenceType(TravelPreferenceType.THINKER)
            .thinkerScore(4)
            .foodieScore(3)
            .artistScore(2)
            .remembererScore(1)
            .build();
    List<Place> places = places(20);
    Map<Long, Place> placesById =
        places.stream().collect(Collectors.toMap(Place::getId, Function.identity()));
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    given(recommendationSetRepository.save(any(RecommendationSet.class)))
        .willAnswer(
            invocation -> {
              RecommendationSet set = invocation.getArgument(0);
              ReflectionTestUtils.setField(set, "id", 12L);
              return set;
            });

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(
            1L,
            new RecommendationDtos.CreateRequest(
                TravelSchedule.DAY_TRIP, LocalDate.of(2099, 8, 20), LocalDate.of(2099, 8, 20)));

    Map<TravelPreferenceType, Long> counts =
        response.batch().places().stream()
            .map(place -> placesById.get(place.placeId()).getTravelMbtiType())
            .collect(
                Collectors.groupingBy(
                    Function.identity(),
                    () -> new EnumMap<>(TravelPreferenceType.class),
                    Collectors.counting()));
    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(response.minimumSelectionCount()).isEqualTo(3);
    assertThat(response.batch().batchNumber()).isEqualTo(1);
    assertThat(response.batch().places()).hasSize(20);
    assertThat(counts)
        .containsEntry(TravelPreferenceType.THINKER, 8L)
        .containsEntry(TravelPreferenceType.FOODIE, 6L)
        .containsEntry(TravelPreferenceType.ARTIST, 4L)
        .containsEntry(TravelPreferenceType.REMEMBERER, 2L);
    verify(placeDetailEnrichmentService)
        .enrichAsync(
            response.batch().places().stream()
                .map(RecommendationDtos.PlaceResponse::placeId)
                .toList());
    verify(tourApiSyncClient, never()).fetchChangedPlaces();
  }

  @Test
  void sendsAtMostSixtyStableCandidatesWithoutUserInformation() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(30);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    ArgumentCaptor<GroqRecommendationClient.RankRequest> captor =
        ArgumentCaptor.forClass(GroqRecommendationClient.RankRequest.class);
    verify(groqRecommendationClient).rank(captor.capture());
    GroqRecommendationClient.RankRequest rankRequest = captor.getValue();
    assertThat(rankRequest.candidates()).hasSize(60);
    assertThat(rankRequest.candidates())
        .filteredOn(candidate -> candidate.preferenceType() == TravelPreferenceType.THINKER)
        .hasSize(15);
    assertThat(rankRequest.scores()).containsEntry(TravelPreferenceType.THINKER, 1);
    assertThat(rankRequest.quotas()).containsEntry(TravelPreferenceType.THINKER, 5);
    assertThat(rankRequest.travelSchedule()).isEqualTo(TravelSchedule.DAY_TRIP);
    assertThat(rankRequest.startDate()).isEqualTo(LocalDate.of(2099, 8, 20));
    assertThat(rankRequest.endDate()).isEqualTo(LocalDate.of(2099, 8, 20));
  }

  @Test
  void savesTheFullyValidAiOrder() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(5);
    AtomicReference<List<Long>> aiOrder = new AtomicReference<>();
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    given(groqRecommendationClient.rank(any()))
        .willAnswer(
            invocation -> {
              List<Long> selected = new ArrayList<>(validSelection(invocation.getArgument(0)));
              java.util.Collections.reverse(selected);
              aiOrder.set(selected);
              return selected;
            });
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactlyElementsOf(aiOrder.get());
    ArgumentCaptor<RecommendationSet> saved = ArgumentCaptor.forClass(RecommendationSet.class);
    verify(recommendationSetRepository).save(saved.capture());
    assertThat(saved.getValue().getRecommendedPlaceIds()).containsExactlyElementsOf(aiOrder.get());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidAiSelections")
  void discardsTheWholeAiResultAndUsesStableFallback(
      String label, Function<GroqRecommendationClient.RankRequest, List<Long>> invalidSelection) {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(20);
    AtomicReference<GroqRecommendationClient.RankRequest> sent = new AtomicReference<>();
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    given(groqRecommendationClient.rank(any()))
        .willAnswer(
            invocation -> {
              GroqRecommendationClient.RankRequest rankRequest = invocation.getArgument(0);
              sent.set(rankRequest);
              return invalidSelection.apply(rankRequest);
            });
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactlyElementsOf(validSelection(sent.get()));
  }

  @Test
  void rejectsAnAiPlaceThatBecameInactiveBeforeSaving() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> places = places(20);
    AtomicReference<Long> inactiveId = new AtomicReference<>();
    List<Place> currentActive = new ArrayList<>(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue())
        .willReturn(places, places)
        .willAnswer(
            invocation ->
                currentActive.stream()
                    .filter(place -> !place.getId().equals(inactiveId.get()))
                    .toList());
    given(groqRecommendationClient.rank(any()))
        .willAnswer(
            invocation -> {
              List<Long> selected = validSelection(invocation.getArgument(0));
              inactiveId.set(selected.getFirst());
              return selected;
            });
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .doesNotContain(inactiveId.get());
  }

  @Test
  void savesOneSyncPageAndUsesANewPlaceWhenInitialQuotaIsShort() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    List<Place> before =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                4,
                TravelPreferenceType.FOODIE,
                5,
                TravelPreferenceType.ARTIST,
                5,
                TravelPreferenceType.REMEMBERER,
                5));
    Place added = place(999L, TravelPreferenceType.THINKER, true);
    List<Place> after = new ArrayList<>(before);
    after.add(added);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(before, before, after);
    given(tourApiSyncClient.fetchChangedPlaces())
        .willReturn(
            List.of(
                syncItem(
                    "광주광역시 북구 무등로 1",
                    "(금곡동)",
                    "999001",
                    "12",
                    "",
                    "126.900001",
                    "35.200001",
                    "1",
                    "새 자연 장소",
                    "NA")));
    given(placeRepository.findExistingTourContentIds(List.of(999001L))).willReturn(Set.of());
    given(
            placeRepository.insertTourApiPlace(
                999001L,
                12,
                "새 자연 장소",
                "자연 관광",
                "THINKER",
                "[\"자연 관광\"]",
                "광주광역시 북구 무등로 1 (금곡동)",
                new BigDecimal("35.200001"),
                new BigDecimal("126.900001"),
                null))
        .willReturn(1);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .contains(999L);
    verify(tourApiSyncClient).fetchChangedPlaces();
  }

  @Test
  void savesOnlySupportedCompleteNewItemsAndMapsAllFourTypes() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 1);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(List.of());
    given(tourApiSyncClient.fetchChangedPlaces())
        .willReturn(
            List.of(
                syncItem("광주", "", "1001", "12", "", "126.8", "35.1", "1", "기존", "NA"),
                syncItem("광주", "", "1002", "12", "", "126.8", "35.1", "1", "자연", "NA"),
                syncItem("광주", "", "1002", "12", "", "126.8", "35.1", "1", "중복", "NA"),
                syncItem("광주", "", "1003", "39", "", "126.8", "35.1", "1", "음식", "FD"),
                syncItem("광주", "", "1004", "14", "", "126.8", "35.1", "1", "문화", "VE"),
                syncItem("광주", "", "1005", "12", "", "126.8", "35.1", "1", "역사", "HS"),
                syncItem("광주", "", "1006", "12", "", "126.8", "35.1", "1", "레저", "LS"),
                syncItem("광주", "", "1012", "12", "", "126.8", "35.1", "1", "숙박 분류", "AC"),
                syncItem("광주", "", "1013", "12", "", "126.8", "35.1", "1", "행사", "EV"),
                syncItem("광주", "", "1014", "12", "", "126.8", "35.1", "1", "체험", "EX"),
                syncItem("광주", "", "1015", "12", "", "126.8", "35.1", "1", "쇼핑", "SH"),
                syncItem("광주", "", "1007", "32", "", "126.8", "35.1", "1", "숙박", "NA"),
                syncItem("광주", "", "1008", "12", "", "126.8", "35.1", "1", "", "NA"),
                syncItem("", "", "1009", "12", "", "126.8", "35.1", "1", "주소 없음", "NA"),
                syncItem("광주", "", "1010", "12", "", "좌표 없음", "35.1", "1", "좌표 오류", "NA"),
                syncItem("광주", "", "1011", "12", "", "126.8", "35.1", "0", "비활성", "NA"),
                syncItem("광주", "", "1016", "12", "", "126.8", "35.1", "1", "긴".repeat(256), "NA"),
                syncItem("광주", "", "1017", "12", "", "126.8", "91", "1", "위도 범위", "NA"),
                syncItem("광주", "", "1018", "12", "", "181", "35.1", "1", "경도 범위", "NA")));
    given(placeRepository.findExistingTourContentIds(any())).willReturn(Set.of(1001L));
    saveWithId(12L);

    recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    verify(tourApiSyncClient).fetchChangedPlaces();
    verify(placeRepository)
        .insertTourApiPlace(
            1002L,
            12,
            "자연",
            "자연 관광",
            "THINKER",
            "[\"자연 관광\"]",
            "광주",
            new BigDecimal("35.1"),
            new BigDecimal("126.8"),
            null);
    verify(placeRepository)
        .insertTourApiPlace(
            1003L,
            39,
            "음식",
            "음식",
            "FOODIE",
            "[\"음식\"]",
            "광주",
            new BigDecimal("35.1"),
            new BigDecimal("126.8"),
            null);
    verify(placeRepository)
        .insertTourApiPlace(
            1004L,
            14,
            "문화",
            "문화 관광",
            "ARTIST",
            "[\"문화 관광\"]",
            "광주",
            new BigDecimal("35.1"),
            new BigDecimal("126.8"),
            null);
    verify(placeRepository)
        .insertTourApiPlace(
            1005L,
            12,
            "역사",
            "역사 관광",
            "REMEMBERER",
            "[\"역사 관광\"]",
            "광주",
            new BigDecimal("35.1"),
            new BigDecimal("126.8"),
            null);
    verify(placeRepository, times(4))
        .insertTourApiPlace(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void continuesWithDatabaseCandidatesWhenTourApiFails() {
    User user = user(TravelPreferenceType.THINKER, 4, 3, 2, 1);
    List<Place> places =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                5,
                TravelPreferenceType.FOODIE,
                20,
                TravelPreferenceType.ARTIST,
                20,
                TravelPreferenceType.REMEMBERER,
                20));
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    given(tourApiSyncClient.fetchChangedPlaces()).willThrow(new IllegalStateException("timeout"));
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById))
        .containsEntry(TravelPreferenceType.THINKER, 5L)
        .containsEntry(TravelPreferenceType.FOODIE, 8L)
        .containsEntry(TravelPreferenceType.ARTIST, 5L)
        .containsEntry(TravelPreferenceType.REMEMBERER, 2L);
    verify(tourApiSyncClient).fetchChangedPlaces();
    verify(placeRepository, never()).findExistingTourContentIds(any());
    InOrder order = inOrder(tourApiSyncClient, transactionTemplate);
    order.verify(tourApiSyncClient).fetchChangedPlaces();
    order.verify(transactionTemplate, times(2)).execute(any());
  }

  @Test
  void redistributesATypeShortageUsingOriginalScores() {
    User user = user(TravelPreferenceType.THINKER, 4, 3, 2, 1);
    List<Place> places =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                5,
                TravelPreferenceType.FOODIE,
                20,
                TravelPreferenceType.ARTIST,
                20,
                TravelPreferenceType.REMEMBERER,
                20));
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById))
        .containsEntry(TravelPreferenceType.THINKER, 5L)
        .containsEntry(TravelPreferenceType.FOODIE, 8L)
        .containsEntry(TravelPreferenceType.ARTIST, 5L)
        .containsEntry(TravelPreferenceType.REMEMBERER, 2L);
    verify(tourApiSyncClient).fetchChangedPlaces();
  }

  @Test
  void breaksLargestRemainderTiesByFixedTypePriority() {
    User user = user(TravelPreferenceType.THINKER, 1, 1, 1, 0);
    List<Place> places = places(20);
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById))
        .containsEntry(TravelPreferenceType.THINKER, 7L)
        .containsEntry(TravelPreferenceType.FOODIE, 7L)
        .containsEntry(TravelPreferenceType.ARTIST, 6L)
        .doesNotContainKey(TravelPreferenceType.REMEMBERER);
  }

  @Test
  void repeatedlyRedistributesZeroScoreShortageByFixedPriority() {
    User user = user(TravelPreferenceType.REMEMBERER, null, 0, -1, null);
    List<Place> places =
        places(
            Map.of(
                TravelPreferenceType.THINKER,
                1,
                TravelPreferenceType.FOODIE,
                20,
                TravelPreferenceType.ARTIST,
                20,
                TravelPreferenceType.REMEMBERER,
                2));
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById))
        .containsEntry(TravelPreferenceType.THINKER, 1L)
        .containsEntry(TravelPreferenceType.FOODIE, 9L)
        .containsEntry(TravelPreferenceType.ARTIST, 8L)
        .containsEntry(TravelPreferenceType.REMEMBERER, 2L);
  }

  @Test
  void calculatesLargeScoresWithoutOverflow() {
    User user =
        user(
            TravelPreferenceType.THINKER,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE);
    List<Place> places = places(20);
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById).values()).containsOnly(5L);
  }

  @Test
  void usesFinalPreferenceWhenAllScoresAreNotPositive() {
    User user = user(TravelPreferenceType.ARTIST, null, 0, -1, -3);
    List<Place> places = places(20);
    Map<Long, Place> placesById = byId(places);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(countTypes(response, placesById))
        .containsOnlyKeys(TravelPreferenceType.ARTIST)
        .containsEntry(TravelPreferenceType.ARTIST, 20L);
  }

  @Test
  void rejectsMissingFinalPreferenceAndInvalidPeriod() {
    User user = user(null, 1, 1, 1, 1);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(
            () -> recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0)))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_PREFERENCE_REQUIRED);

    assertThatThrownBy(
            () ->
                recommendationService.createOrGetCurrent(
                    1L, request(TravelSchedule.ONE_NIGHT_TWO_DAYS, 0)))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_INVALID_PERIOD);

    LocalDate yesterday = LocalDate.now().minusDays(1);
    assertThatThrownBy(
            () ->
                recommendationService.createOrGetCurrent(
                    1L,
                    new RecommendationDtos.CreateRequest(
                        TravelSchedule.DAY_TRIP, yesterday, yesterday)))
        .isInstanceOf(RecommendationHandler.class)
        .extracting("code")
        .isEqualTo(ErrorStatus.RECOMMENDATION_INVALID_PERIOD);
  }

  @Test
  void returnsStoredCurrentSetWithoutRegeneratingIt() {
    User user = user(TravelPreferenceType.THINKER, 4, 3, 2, 1);
    Place place = places(1).getFirst();
    RecommendationSet stored =
        recommendationSet(
            12L,
            TravelSchedule.DAY_TRIP,
            List.of(place.getId()),
            List.of(place.getId()),
            List.of());
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(List.of(place.getId()))).willReturn(List.of(place));

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactly(place.getId());
    assertThat(stored.getLikedPlaceIds()).containsExactly(place.getId());
    verify(placeRepository, never()).findAllByActiveTrue();
    verify(tourApiSyncClient, never()).fetchChangedPlaces();
    verify(placeDetailEnrichmentService, never()).enrichAsync(any());
    verify(recommendationSetRepository, never()).save(any());
  }

  @Test
  void keepsIdAndClearsReactionsWhenPeriodChanges() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    List<Place> places = places(20);
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, List.of(1L), List.of(1L), List.of(2L));
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    given(recommendationSetRepository.save(stored)).willReturn(stored);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.ONE_NIGHT_TWO_DAYS, 1));

    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(stored.getTravelSchedule()).isEqualTo(TravelSchedule.ONE_NIGHT_TWO_DAYS);
    assertThat(stored.getLikedPlaceIds()).isEmpty();
    assertThat(stored.getDislikedPlaceIds()).isEmpty();
    verify(placeDetailEnrichmentService)
        .enrichAsync(
            response.batch().places().stream()
                .map(RecommendationDtos.PlaceResponse::placeId)
                .toList());
  }

  @Test
  void returnsSavedRecommendationWhenEnrichmentSchedulingFails() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    List<Place> places = places(20);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);
    willThrow(new IllegalStateException("failure"))
        .given(placeDetailEnrichmentService)
        .enrichAsync(any());

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.recommendationId()).isEqualTo(12L);
    assertThat(response.batch().places()).hasSize(20);
  }

  @Test
  void skipsMissingAndInactivePlacesInTheStoredLastBatch() {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    List<Long> ids = new ArrayList<>();
    for (long id = 1; id <= 40; id++) {
      ids.add(id);
    }
    RecommendationSet stored =
        recommendationSet(12L, TravelSchedule.DAY_TRIP, ids, List.of(), List.of());
    Place active = place(21L, TravelPreferenceType.THINKER, true);
    Place inactive = place(22L, TravelPreferenceType.THINKER, false);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(stored));
    given(placeRepository.findAllById(ids.subList(20, 40))).willReturn(List.of(active, inactive));

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(response.batch().batchNumber()).isEqualTo(2);
    assertThat(response.batch().places())
        .extracting(RecommendationDtos.PlaceResponse::placeId)
        .containsExactly(21L);
  }

  @ParameterizedTest
  @CsvSource({
    "DAY_TRIP, 0, 3",
    "ONE_NIGHT_TWO_DAYS, 1, 5",
    "TWO_NIGHTS_THREE_DAYS, 2, 7",
    "CUSTOM, 3, 20"
  })
  void appliesPeriodRulesAndMinimumSelectionCount(
      TravelSchedule schedule, int days, int minimumSelectionCount) {
    User user = user(TravelPreferenceType.THINKER, 1, 0, 0, 0);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(List.of());
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse response =
        recommendationService.createOrGetCurrent(1L, request(schedule, days));

    assertThat(response.minimumSelectionCount()).isEqualTo(minimumSelectionCount);
    assertThat(response.batch().places()).isEmpty();
    verify(groqRecommendationClient, never()).rank(any());
  }

  @Test
  void returnsAllAvailablePlacesInStableInterleavedOrder() {
    User user = user(TravelPreferenceType.THINKER, 4, 3, 2, 1);
    List<Place> places = places(2);
    given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.empty());
    given(placeRepository.findAllByActiveTrue()).willReturn(places);
    saveWithId(12L);

    RecommendationDtos.RecommendationResponse first =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));
    RecommendationDtos.RecommendationResponse second =
        recommendationService.createOrGetCurrent(1L, request(TravelSchedule.DAY_TRIP, 0));

    assertThat(first.batch().places()).hasSize(8);
    assertThat(second.batch().places()).isEqualTo(first.batch().places());
    assertThat(first.batch().places().subList(0, 4))
        .extracting(place -> byId(places).get(place.placeId()).getTravelMbtiType())
        .containsExactlyElementsOf(List.of(TravelPreferenceType.values()));
  }

  private static User user(
      TravelPreferenceType type,
      Integer thinker,
      Integer foodie,
      Integer artist,
      Integer rememberer) {
    return User.builder()
        .nickname("여행자")
        .identificationCode(1)
        .preferenceType(type)
        .thinkerScore(thinker)
        .foodieScore(foodie)
        .artistScore(artist)
        .remembererScore(rememberer)
        .build();
  }

  private static RecommendationDtos.CreateRequest request(TravelSchedule schedule, int days) {
    LocalDate start = LocalDate.of(2099, 8, 20);
    return new RecommendationDtos.CreateRequest(schedule, start, start.plusDays(days));
  }

  private void saveWithId(long id) {
    given(recommendationSetRepository.save(any(RecommendationSet.class)))
        .willAnswer(
            invocation -> {
              RecommendationSet set = invocation.getArgument(0);
              ReflectionTestUtils.setField(set, "id", id);
              return set;
            });
  }

  private static RecommendationSet recommendationSet(
      long id,
      TravelSchedule schedule,
      List<Long> recommended,
      List<Long> liked,
      List<Long> disliked) {
    LocalDate start = LocalDate.of(2099, 8, 20);
    RecommendationSet set =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(schedule)
            .startDate(start)
            .endDate(start.plusDays(schedule == TravelSchedule.DAY_TRIP ? 0 : 1))
            .recommendedPlaceIds(recommended)
            .likedPlaceIds(liked)
            .dislikedPlaceIds(disliked)
            .build();
    ReflectionTestUtils.setField(set, "id", id);
    return set;
  }

  private static Map<Long, Place> byId(List<Place> places) {
    return places.stream().collect(Collectors.toMap(Place::getId, Function.identity()));
  }

  private static java.util.stream.Stream<Arguments> invalidAiSelections() {
    return java.util.stream.Stream.of(
        Arguments.of(
            "임의 ID",
            (Function<GroqRecommendationClient.RankRequest, List<Long>>)
                request -> {
                  List<Long> ids = new ArrayList<>(validSelection(request));
                  ids.set(0, 999_999L);
                  return ids;
                }),
        Arguments.of(
            "중복 ID",
            (Function<GroqRecommendationClient.RankRequest, List<Long>>)
                request -> {
                  List<Long> ids = new ArrayList<>(validSelection(request));
                  ids.set(1, ids.getFirst());
                  return ids;
                }),
        Arguments.of(
            "수량 부족",
            (Function<GroqRecommendationClient.RankRequest, List<Long>>)
                request -> validSelection(request).subList(1, validSelection(request).size())),
        Arguments.of(
            "유형 할당량 위반",
            (Function<GroqRecommendationClient.RankRequest, List<Long>>)
                request -> {
                  List<Long> ids = new ArrayList<>(validSelection(request));
                  Long extraFoodie =
                      request.candidates().stream()
                          .filter(
                              candidate ->
                                  candidate.preferenceType() == TravelPreferenceType.FOODIE
                                      && !ids.contains(candidate.placeId()))
                          .findFirst()
                          .orElseThrow()
                          .placeId();
                  int thinkerIndex =
                      java.util.stream.IntStream.range(0, ids.size())
                          .filter(
                              index ->
                                  typeOf(request, ids.get(index)) == TravelPreferenceType.THINKER)
                          .findFirst()
                          .orElseThrow();
                  ids.set(thinkerIndex, extraFoodie);
                  return ids;
                }),
        Arguments.of(
            "외부 실패",
            (Function<GroqRecommendationClient.RankRequest, List<Long>>) request -> List.of()));
  }

  private static java.util.stream.Stream<Arguments> invalidReactionRequests() {
    return java.util.stream.Stream.of(
        Arguments.of(new RecommendationDtos.ReactionRequest(List.of(1L), List.of(2L))),
        Arguments.of(new RecommendationDtos.ReactionRequest(List.of(1L, 1L), List.of(2L, 3L))),
        Arguments.of(new RecommendationDtos.ReactionRequest(List.of(1L), List.of(2L, 2L, 3L))),
        Arguments.of(new RecommendationDtos.ReactionRequest(List.of(1L, 2L), List.of(2L, 3L))),
        Arguments.of(new RecommendationDtos.ReactionRequest(List.of(1L, 4L), List.of(2L, 3L))),
        Arguments.of(
            new RecommendationDtos.ReactionRequest(
                java.util.Arrays.asList(1L, null), List.of(2L, 3L))));
  }

  private static List<Long> validSelection(GroqRecommendationClient.RankRequest request) {
    Map<TravelPreferenceType, List<Long>> selected = new EnumMap<>(TravelPreferenceType.class);
    for (TravelPreferenceType type : TravelPreferenceType.values()) {
      selected.put(
          type,
          request.candidates().stream()
              .filter(candidate -> candidate.preferenceType() == type)
              .limit(request.quotas().get(type))
              .map(GroqRecommendationClient.Candidate::placeId)
              .toList());
    }
    List<Long> result = new ArrayList<>();
    for (int index = 0;
        result.size() < request.quotas().values().stream().mapToInt(Integer::intValue).sum();
        index++) {
      for (TravelPreferenceType type : TravelPreferenceType.values()) {
        if (index < selected.get(type).size()) {
          result.add(selected.get(type).get(index));
        }
      }
    }
    return result;
  }

  private static TravelPreferenceType typeOf(
      GroqRecommendationClient.RankRequest request, Long placeId) {
    return request.candidates().stream()
        .filter(candidate -> candidate.placeId().equals(placeId))
        .findFirst()
        .orElseThrow()
        .preferenceType();
  }

  private static Map<TravelPreferenceType, Long> countTypes(
      RecommendationDtos.RecommendationResponse response, Map<Long, Place> placesById) {
    return response.batch().places().stream()
        .map(place -> placesById.get(place.placeId()).getTravelMbtiType())
        .collect(
            Collectors.groupingBy(
                Function.identity(),
                () -> new EnumMap<>(TravelPreferenceType.class),
                Collectors.counting()));
  }

  private static List<Place> places(int countPerType) {
    Map<TravelPreferenceType, Integer> counts = new EnumMap<>(TravelPreferenceType.class);
    for (TravelPreferenceType type : TravelPreferenceType.values()) {
      counts.put(type, countPerType);
    }
    return places(counts);
  }

  private static List<Place> places(Map<TravelPreferenceType, Integer> counts) {
    List<Place> places = new ArrayList<>();
    long id = 1;
    for (TravelPreferenceType type : TravelPreferenceType.values()) {
      for (int i = 0; i < counts.getOrDefault(type, 0); i++) {
        places.add(place(id++, type, true));
      }
    }
    return places;
  }

  private static Place place(long id, TravelPreferenceType type, boolean active) {
    Place place =
        Place.builder()
            .name(type + " 장소 " + id)
            .category("카테고리")
            .travelMbtiType(type)
            .tags(List.of("태그"))
            .address("광주")
            .latitude(BigDecimal.valueOf(35.1))
            .longitude(BigDecimal.valueOf(126.8))
            .active(active)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private static TourApiSyncClient.SyncItem syncItem(
      String address,
      String addressDetail,
      String contentId,
      String contentTypeId,
      String thumbnailUrl,
      String longitude,
      String latitude,
      String showFlag,
      String title,
      String largeCategoryCode) {
    return new TourApiSyncClient.SyncItem(
        address,
        addressDetail,
        contentId,
        contentTypeId,
        thumbnailUrl,
        longitude,
        latitude,
        showFlag,
        title,
        largeCategoryCode,
        largeCategoryCode + "01",
        largeCategoryCode + "010100");
  }
}
