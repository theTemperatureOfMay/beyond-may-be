package com.example.beyond_may_be.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            authorize -> {
              authorize.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
              if (environment.matchesProfiles("performance")) {
                authorize.requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll();
              }
              authorize
                  .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/api/v1/users/sign-up", "/api/v1/users/login")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/preference-tests/questions")
                  .permitAll()
                  .anyRequest()
                  .denyAll();
            })
        .build();
  }
}
