package com.example.beyond_may_be.question.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.question.dto.PreferenceTestDtos.AnswerRequest;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultRequest;
import com.example.beyond_may_be.question.service.PreferenceTestService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceTestControllerTest {

  @Test
  void delegatesResultSaveToService() {
    PreferenceTestService preferenceTestService = mock(PreferenceTestService.class);
    PreferenceTestController controller = new PreferenceTestController(preferenceTestService);
    ResultRequest request = new ResultRequest(List.of(new AnswerRequest("1", 0)));

    when(preferenceTestService.saveResult(request))
        .thenThrow(new IllegalStateException("서비스가 호출되었습니다."));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> controller.saveResult(request));

    assertEquals("서비스가 호출되었습니다.", exception.getMessage());
    verify(preferenceTestService).saveResult(request);
  }
}
