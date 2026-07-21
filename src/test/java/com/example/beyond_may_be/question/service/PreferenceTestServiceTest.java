package com.example.beyond_may_be.question.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.question.dto.PreferenceTestDtos.AnswerRequest;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultRequest;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreferenceTestServiceTest {

  @Test
  void returnsTemporaryResultResponse() {
    PreferenceTestService service = new PreferenceTestService();

    ResultResponse response =
        service.saveResult(new ResultRequest(List.of(new AnswerRequest("1", 0))));

    assertEquals("temporary-result-token", response.resultToken());
    assertEquals("사색러", response.preferenceType());
    assertEquals("성향 검사 결과 설명입니다.", response.description());
    assertEquals(Map.of("사색러", 0, "미식러", 0, "예술러", 0, "기억러", 0), response.scoreDetail());
  }
}
