package com.example.beyond_may_be.route.dto;

import tools.jackson.databind.JsonNode;

public final class RouteDtos {

  private RouteDtos() {}

  public record RouteResponse(JsonNode walking, JsonNode publicTransit) {}

  public record RouteResult(RouteResponse response, boolean partial) {}
}
