package com.example.beyond_may_be.place.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceDetailEnrichmentServiceTest {

  @InjectMocks private PlaceDetailEnrichmentService service;

  @Mock private PlaceRepository placeRepository;
  @Mock private TourApiPlaceDetailClient detailClient;

  @Test
  void fetchesOnlyMissingFieldsAndStoresProvidedValues() {
    Place missingDescription = place(1L, 1001L, 12, null, "09:00~18:00");
    Place missingHours = place(2L, 1002L, 39, "기존 설명", " ");
    Place withoutTourId = place(3L, null, null, null, null);
    Place withoutContentType = place(4L, 1004L, null, "기존 설명", null);
    given(placeRepository.findAllById(List.of(1L, 2L, 3L, 4L)))
        .willReturn(List.of(missingDescription, missingHours, withoutTourId, withoutContentType));
    given(detailClient.fetchDescription(1001L)).willReturn("새 설명");
    given(detailClient.fetchBusinessHours(1002L, 39)).willReturn("10:00~20:00");

    service.enrichAsync(List.of(1L, 2L, 3L, 4L));

    verify(detailClient).fetchDescription(1001L);
    verify(detailClient, never()).fetchBusinessHours(1001L, 12);
    verify(detailClient, never()).fetchDescription(1002L);
    verify(detailClient).fetchBusinessHours(1002L, 39);
    verifyNoMoreInteractions(detailClient);
    verify(placeRepository).updateDescriptionIfMissing(1L, "새 설명");
    verify(placeRepository).updateBusinessHoursIfMissing(2L, "10:00~20:00");
  }

  @Test
  void keepsMissingFieldsNullWhenTourApiDoesNotProvideValues() {
    Place place = place(1L, 1001L, 12, null, null);
    given(placeRepository.findAllById(List.of(1L))).willReturn(List.of(place));
    given(detailClient.fetchDescription(1001L)).willReturn(" ");
    given(detailClient.fetchBusinessHours(1001L, 12)).willReturn(null);

    service.enrichAsync(List.of(1L));

    verify(placeRepository, never()).updateDescriptionIfMissing(eq(1L), anyString());
    verify(placeRepository, never()).updateBusinessHoursIfMissing(eq(1L), anyString());
  }

  @Test
  void continuesWithOtherPlacesAfterOnePlaceFails() {
    Place failed = place(1L, 1001L, 12, null, "기존 운영시간");
    Place successful = place(2L, 1002L, 14, null, "기존 운영시간");
    given(placeRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(failed, successful));
    given(detailClient.fetchDescription(1001L)).willThrow(new IllegalStateException("failure"));
    given(detailClient.fetchDescription(1002L)).willReturn("두 번째 장소 설명");

    service.enrichAsync(List.of(1L, 2L));

    verify(detailClient).fetchDescription(1002L);
    verify(placeRepository).updateDescriptionIfMissing(2L, "두 번째 장소 설명");
  }

  private Place place(
      Long id,
      Long tourContentId,
      Integer tourContentTypeId,
      String description,
      String businessHours) {
    Place place =
        Place.builder()
            .name("장소 " + id)
            .category("관광")
            .travelMbtiType(TravelPreferenceType.THINKER)
            .tags(List.of("관광"))
            .address("광주광역시")
            .latitude(BigDecimal.valueOf(35.1))
            .longitude(BigDecimal.valueOf(126.9))
            .description(description)
            .businessHours(businessHours)
            .tourContentId(tourContentId)
            .tourContentTypeId(tourContentTypeId)
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }
}
