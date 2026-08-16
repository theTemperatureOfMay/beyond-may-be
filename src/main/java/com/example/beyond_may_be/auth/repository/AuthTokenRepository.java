package com.example.beyond_may_be.auth.repository;

import com.example.beyond_may_be.auth.domain.AuthToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
  Optional<AuthToken> findByToken(String token);
}
