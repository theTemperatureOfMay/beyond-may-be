package com.example.beyond_may_be.preference.repository;

import com.example.beyond_may_be.preference.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
  List<Question> findByActiveTrue();
}
