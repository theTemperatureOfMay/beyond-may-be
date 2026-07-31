package com.example.beyond_may_be.user.repository;

import com.example.beyond_may_be.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByNicknameAndIdentificationCode(String nickname, Integer identificationCode);

  Optional<User> findByNicknameAndIdentificationCode(String nickname, Integer identificationCode);
}
