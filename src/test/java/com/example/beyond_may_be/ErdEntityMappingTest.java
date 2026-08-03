package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.user.domain.User;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  private static void assertStringEnum(Class<?> type, String fieldName)
      throws NoSuchFieldException {
    Field field = type.getDeclaredField(fieldName);
    assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
  }
}
