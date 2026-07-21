package com.example.beyond_may_be.question.service;

import com.example.beyond_may_be.mbti.domain.enums.MbtiType;
import com.example.beyond_may_be.question.converter.PreferenceTestConverter;
import com.example.beyond_may_be.question.domain.Question;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultRequest;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PreferenceTestService {

  public QuestionsResponse getQuestions() {
    List<Question> questions = getRandomQuestions();
    return PreferenceTestConverter.toQuestionsResponse(questions);
  }

  public ResultResponse saveResult(ResultRequest request) {
    return PreferenceTestConverter.toResultResponse(
        "temporary-result-token", MbtiType.THINKER, "성향 검사 결과 설명입니다.", Map.of());
  }

  private List<Question> getRandomQuestions() {
    throw new UnsupportedOperationException("Not implemented");
  }
}
