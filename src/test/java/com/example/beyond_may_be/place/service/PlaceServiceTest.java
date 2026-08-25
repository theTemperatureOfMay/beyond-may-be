package com.example.beyond_may_be.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

  @Mock private PlaceRepository placeRepository;
  @Mock private TourApiPlaceDetailClient detailClient;

  @Test
  void returnsActivePlaceCatalogDetail() {
    Place place = completePlace(101L);
    given(placeRepository.findByIdAndActiveTrue(101L)).willReturn(Optional.of(place));
    PlaceService service = service();

    PlaceDtos.DetailResponse result = service.getDetail(101L);

    assertAll(
        () -> assertEquals(101L, result.placeId()),
        () -> assertEquals("국립아시아문화전당", result.name()),
        () -> assertEquals("전시", result.category()),
        () -> assertEquals(TravelPreferenceType.ARTIST, result.travelMbtiType()),
        () -> assertEquals(List.of("전시", "문화"), result.tags()),
        () -> assertEquals("광주광역시 동구 문화전당로 38", result.address()),
        () -> assertEquals(new BigDecimal("35.146600"), result.latitude()),
        () -> assertEquals(new BigDecimal("126.919900"), result.longitude()),
        () -> assertEquals("평일 10:00-18:00", result.businessHours()),
        () -> assertEquals("검수된 상세 설명", result.description()),
        () -> assertEquals("https://example.com/places/101.webp", result.thumbnailUrl()));
    verifyNoInteractions(detailClient);
  }

  @Test
  void rejectsPlaceThatIsMissingOrInactive() {
    given(placeRepository.findByIdAndActiveTrue(404L)).willReturn(Optional.empty());
    PlaceService service = service();

    GeneralException exception =
        assertThrows(GeneralException.class, () -> service.getDetail(404L));

    assertSame(ErrorStatus.PLACE_DETAIL_NOT_FOUND, exception.getCode());
  }

  @Test
  void enrichesMissingFieldsSynchronouslyAndReturnsStoredValues() {
    Place missing = place(102L, null, " ", 1002L, 12);
    Place enriched = place(102L, "TourAPI 상세 설명", "매일 09:00-18:00", 1002L, 12);
    given(placeRepository.findByIdAndActiveTrue(102L))
        .willReturn(Optional.of(missing), Optional.of(enriched));
    given(detailClient.fetchDescription(1002L)).willReturn("TourAPI 상세 설명");
    given(detailClient.fetchBusinessHours(1002L, 12)).willReturn("매일 09:00-18:00");

    PlaceDtos.DetailResponse result = service().getDetail(102L);

    assertAll(
        () -> assertEquals("TourAPI 상세 설명", result.description()),
        () -> assertEquals("매일 09:00-18:00", result.businessHours()));
    verify(placeRepository).updateDescriptionIfMissing(102L, "TourAPI 상세 설명");
    verify(placeRepository).updateBusinessHoursIfMissing(102L, "매일 09:00-18:00");
  }

  @Test
  void returnsServiceUnavailableAndKeepsMissingValueWhenTourApiFails() {
    Place missing = place(103L, null, null, 1003L, 12);
    given(placeRepository.findByIdAndActiveTrue(103L)).willReturn(Optional.of(missing));
    given(detailClient.fetchDescription(1003L))
        .willThrow(new TourApiPlaceDetailClient.TourApiPlaceDetailException());
    given(detailClient.fetchBusinessHours(1003L, 12)).willReturn("매일 10:00-19:00");

    GeneralException exception =
        assertThrows(GeneralException.class, () -> service().getDetail(103L));

    assertSame(ErrorStatus.PLACE_DETAIL_UNAVAILABLE, exception.getCode());
    verify(placeRepository, never()).updateDescriptionIfMissing(eq(103L), anyString());
    verify(placeRepository).updateBusinessHoursIfMissing(103L, "매일 10:00-19:00");
  }

  @DisplayName("해당 유형의 활성 장소가 6곳을 초과하면 6곳만 랜덤으로 반환한다.")
  @Test
  void getRecommendations_moreThanSix_returnsSix() {
    // given
    List<Place> places =
        java.util.stream.IntStream.rangeClosed(1, 10)
            .mapToObj(i -> recommendationPlace("장소" + i, TravelPreferenceType.THINKER))
            .toList();
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.THINKER))
        .willReturn(places);

    // when
    PlaceRecommendationResponse response =
        service().getRecommendations(TravelPreferenceType.THINKER);

    // then
    assertThat(response.places()).hasSize(6);
    List<String> allNames = places.stream().map(Place::getName).toList();
    assertThat(response.places()).allMatch(p -> allNames.contains(p.name()));
    assertThat(response.places().stream().map(p -> p.name()).distinct()).hasSize(6);
  }

  @DisplayName("해당 유형의 활성 장소가 6곳 미만이면 있는 만큼만 반환한다.")
  @Test
  void getRecommendations_fewerThanSix_returnsAll() {
    // given
    List<Place> places =
        List.of(
            recommendationPlace("장소1", TravelPreferenceType.FOODIE),
            recommendationPlace("장소2", TravelPreferenceType.FOODIE));
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.FOODIE))
        .willReturn(places);

    // when
    PlaceRecommendationResponse response =
        service().getRecommendations(TravelPreferenceType.FOODIE);

    // then
    assertThat(response.places()).hasSize(2);
  }

  @DisplayName("해당 유형의 활성 장소가 없으면 빈 목록을 반환한다.")
  @Test
  void getRecommendations_none_returnsEmpty() {
    // given
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.ARTIST))
        .willReturn(List.of());

    // when
    PlaceRecommendationResponse response =
        service().getRecommendations(TravelPreferenceType.ARTIST);

    // then
    assertThat(response.places()).isEmpty();
  }

  private PlaceService service() {
    return new PlaceService(
        placeRepository, new PlaceDetailEnrichmentService(placeRepository, detailClient));
  }

  private Place completePlace(Long id) {
    return place(id, "검수된 상세 설명", "평일 10:00-18:00", 1001L, 14);
  }

  private Place place(
      Long id,
      String description,
      String businessHours,
      Long tourContentId,
      Integer tourContentTypeId) {
    Place place =
        Place.builder()
            .name("국립아시아문화전당")
            .category("전시")
            .travelMbtiType(TravelPreferenceType.ARTIST)
            .tags(List.of("전시", "문화"))
            .address("광주광역시 동구 문화전당로 38")
            .latitude(new BigDecimal("35.146600"))
            .longitude(new BigDecimal("126.919900"))
            .businessHours(businessHours)
            .description(description)
            .thumbnailUrl("https://example.com/places/101.webp")
            .tourContentId(tourContentId)
            .tourContentTypeId(tourContentTypeId)
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private Place recommendationPlace(String name, TravelPreferenceType type) {
    return Place.builder()
        .name(name)
        .category("카테고리")
        .travelMbtiType(type)
        .tags(List.of("태그"))
        .address("주소")
        .latitude(BigDecimal.valueOf(35.1595))
        .longitude(BigDecimal.valueOf(126.8526))
        .businessHours("09:00-18:00")
        .description("설명")
        .thumbnailUrl(null)
        .active(true)
        .build();
  }
}
