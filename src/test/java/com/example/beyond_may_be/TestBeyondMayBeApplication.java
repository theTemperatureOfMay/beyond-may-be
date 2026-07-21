package com.example.beyond_may_be;

import org.springframework.boot.SpringApplication;

public class TestBeyondMayBeApplication {

  public static void main(String[] args) {
    SpringApplication.from(BeyondMayBeApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
