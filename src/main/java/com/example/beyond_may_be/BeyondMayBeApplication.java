package com.example.beyond_may_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class BeyondMayBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(BeyondMayBeApplication.class, args);
  }
}
