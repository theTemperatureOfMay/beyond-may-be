package com.example.beyond_may_be.preference.service;

import com.example.beyond_may_be.preference.converter.PreferenceTestConverter;
import com.example.beyond_may_be.preference.domain.Question;
import com.example.beyond_may_be.preference.domain.QuestionOption;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.preference.repository.QuestionOptionRepository;
import com.example.beyond_may_be.preference.repository.QuestionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceTestService {

  private final QuestionRepository questionRepository;
  private final QuestionOptionRepository questionOptionRepository;

  public QuestionsResponse getQuestions() {
    List<Question> questions = questionRepository.findByActiveTrue();
    List<Long> questionIds = questions.stream().map(Question::getId).toList();

    List<QuestionOption> options =
        questionOptionRepository.findByQuestionIdInOrderByQuestionIdAscDisplayOrderAsc(questionIds);
    Map<Long, List<QuestionOption>> optionsByQuestionId =
        options.stream().collect(Collectors.groupingBy(QuestionOption::getQuestionId));

    return PreferenceTestConverter.toQuestionsResponse(questions, optionsByQuestionId);
  }
}
