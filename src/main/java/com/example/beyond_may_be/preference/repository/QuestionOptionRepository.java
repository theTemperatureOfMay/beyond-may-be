package com.example.beyond_may_be.preference.repository;

import com.example.beyond_may_be.preference.domain.QuestionOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
  List<QuestionOption> findByQuestionIdInOrderByQuestionIdAscDisplayOrderAsc(
      List<Long> questionIds);
}
