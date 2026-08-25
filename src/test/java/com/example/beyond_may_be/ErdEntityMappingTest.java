package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ErdEntityMappingTest {

  @Test
  void enumFieldsUseStableStringMapping() throws NoSuchFieldException {
    assertStringEnum(User.class, "preferenceType");
    assertStringEnum(Place.class, "travelMbtiType");
    assertStringEnum(RecommendationSet.class, "travelSchedule");
    assertStringEnum(Course.class, "status");
    assertStringEnum(Course.class, "travelSchedule");
    assertStringEnum(Exploration.class, "status");
    assertStringEnum(ExplorationParticipant.class, "role");
    assertStringEnum(ExplorationParticipant.class, "status");
  }

  @Test
  void placeDetailsAllowUnknownValues() throws NoSuchFieldException {
    Column businessHours =
        Place.class.getDeclaredField("businessHours").getAnnotation(Column.class);
    assertTrue(businessHours.nullable());
    assertEquals("text", businessHours.columnDefinition());
    assertTrue(Place.class.getDeclaredField("description").getAnnotation(Column.class).nullable());
  }

  @Test
  void placeStoresTourApiIdentifiers() throws NoSuchFieldException {
    Column contentId = Place.class.getDeclaredField("tourContentId").getAnnotation(Column.class);
    Column contentTypeId =
        Place.class.getDeclaredField("tourContentTypeId").getAnnotation(Column.class);

    assertEquals("tour_content_id", contentId.name());
    assertTrue(contentId.nullable());
    assertEquals("tour_content_type_id", contentTypeId.name());
    assertTrue(contentTypeId.nullable());

    Table table = Place.class.getAnnotation(Table.class);
    assertEquals(1, table.uniqueConstraints().length);
    assertEquals("tour_content_id", table.uniqueConstraints()[0].columnNames()[0]);
  }

  private static void assertStringEnum(Class<?> type, String fieldName)
      throws NoSuchFieldException {
    Field field = type.getDeclaredField(fieldName);
    assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
  }
}
