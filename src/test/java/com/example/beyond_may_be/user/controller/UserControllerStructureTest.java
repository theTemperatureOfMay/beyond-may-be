package com.example.beyond_may_be.user.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class UserControllerStructureTest {

  @Test
  void declaresUserAndAuthEndpoints() {
    assertEndpoint(
        "com.example.beyond_may_be.user.controller.UserController",
        "createNickname",
        PostMapping.class,
        "/api/users/nickname");
    assertEndpoint(
        "com.example.beyond_may_be.user.controller.AuthController",
        "login",
        PostMapping.class,
        "/api/auth/login");
    assertEndpoint(
        "com.example.beyond_may_be.user.controller.UserController",
        "getPreference",
        GetMapping.class,
        "/api/users/{userId}/preference");
  }
}
