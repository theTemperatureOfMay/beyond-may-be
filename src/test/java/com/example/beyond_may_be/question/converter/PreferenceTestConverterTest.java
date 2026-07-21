package com.example.beyond_may_be.question.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceTestConverterTest {

  @Test
  void resultResponseContainsOnlySpecifiedFields() {
    List<String> fieldNames =
        Arrays.stream(ResultResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertEquals(
        List.of("resultToken", "preferenceType", "description", "scoreDetail"), fieldNames);
  }
}
