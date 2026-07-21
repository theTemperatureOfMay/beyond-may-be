package com.example.beyond_may_be.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Beyond May Be API")
                .description("광주 여행 동행 서비스 API 문서입니다.")
                .version("v1"));
  }
}
