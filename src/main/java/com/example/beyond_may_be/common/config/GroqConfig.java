package com.example.beyond_may_be.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GroqConfig {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @Bean("groqRestClient")
  RestClient groqRestClient(@Value("${groq.base-url}") String baseUrl) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
