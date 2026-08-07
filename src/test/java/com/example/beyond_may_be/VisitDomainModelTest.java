package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.visit.domain.Visit;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VisitDomainModelTest {

  @Test
  void placeIsRequiredAndCoursePlaceIsOptional() throws NoSuchFieldException {
    Field placeId = Visit.class.getDeclaredField("placeId");
    Field coursePlaceId = Visit.class.getDeclaredField("coursePlaceId");

    assertFalse(placeId.getAnnotation(Column.class).nullable());
    assertTrue(coursePlaceId.getAnnotation(Column.class).nullable());
  }

  @Test
  void participantCannotVisitTheSamePlaceTwice() {
    Table table = Visit.class.getAnnotation(Table.class);

    assertEquals(1, table.uniqueConstraints().length);
    assertArrayEquals(
        new String[] {"participant_id", "place_id"}, table.uniqueConstraints()[0].columnNames());
  }

  @Test
  void nearbyPlaceVisitDoesNotRequireCoursePlace() {
    LocalDateTime visitedAt = LocalDateTime.of(2026, 8, 7, 12, 30);

    Visit visit = new Visit(3L, 5L, null, visitedAt);

    assertEquals(3L, visit.getParticipantId());
    assertEquals(5L, visit.getPlaceId());
    assertNull(visit.getCoursePlaceId());
    assertEquals(visitedAt, visit.getVisitedAt());
  }
}
