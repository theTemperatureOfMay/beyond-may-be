package com.example.beyond_may_be.route.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;
import com.example.beyond_may_be.route.client.KakaoRouteClient;
import com.example.beyond_may_be.route.dto.RouteDtos;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RouteService {

  private final KakaoRouteClient kakaoRouteClient;

  public RouteService(KakaoRouteClient kakaoRouteClient) {
    this.kakaoRouteClient = kakaoRouteClient;
  }

  public RouteDtos.RouteResponse getRoute(
      BigDecimal startLng, BigDecimal startLat, BigDecimal endLng, BigDecimal endLat) {
    JsonNode walkingResponse;
    JsonNode publicTransitResponse;
    try {
      walkingResponse = kakaoRouteClient.fetchWalkingRoute(startLng, startLat, endLng, endLat);
      publicTransitResponse =
          kakaoRouteClient.fetchPublicTransitRoute(startLng, startLat, endLng, endLat);
    } catch (RestClientException exception) {
      throw new GeneralException(ErrorStatus.ROUTE_UNAVAILABLE);
    }

    JsonNode walking = walkingRoute(walkingResponse);
    JsonNode publicTransit = publicTransitRoute(publicTransitResponse);
    try {
      if (publicTransit != null) {
        publicTransit = mergeWalkingSegments(publicTransit, startLng, startLat, endLng, endLat);
      }
    } catch (RestClientException exception) {
      throw new GeneralException(ErrorStatus.ROUTE_UNAVAILABLE);
    }

    if (walking == null && publicTransit == null) {
      throw new GeneralException(ErrorStatus.ROUTE_NOT_FOUND);
    }
    return new RouteDtos.RouteResponse(walking, publicTransit);
  }

  private JsonNode walkingRoute(JsonNode response) {
    if (!isSuccessful(response)) {
      return null;
    }
    JsonNode route = response.get("route");
    return route != null && route.isObject() ? route : null;
  }

  private JsonNode publicTransitRoute(JsonNode response) {
    if (!isSuccessful(response)) {
      return null;
    }
    JsonNode routes = response.get("routes");
    return routes != null && routes.isArray() && routes.size() > 0 ? routes.get(0) : null;
  }

  private JsonNode mergeWalkingSegments(
      JsonNode route,
      BigDecimal startLng,
      BigDecimal startLat,
      BigDecimal endLng,
      BigDecimal endLat) {
    JsonNode steps = route.get("steps");
    if (steps == null || !steps.isArray() || steps.isEmpty()) {
      return route;
    }

    int firstTransitIndex = firstTransitStep(steps);
    int lastTransitIndex = lastTransitStep(steps);
    if (firstTransitIndex < 0) {
      return route;
    }

    JsonNode beforeBoarding = null;
    JsonNode afterAlighting = null;
    if (firstTransitIndex == 0) {
      beforeBoarding =
          kakaoRouteClient.fetchWalkingRoute(
              startLng,
              startLat,
              coordinate(steps.get(firstTransitIndex), 0, 0),
              coordinate(steps.get(firstTransitIndex), 0, 1));
    }
    if (lastTransitIndex == steps.size() - 1) {
      afterAlighting =
          kakaoRouteClient.fetchWalkingRoute(
              coordinate(steps.get(lastTransitIndex), -1, 0),
              coordinate(steps.get(lastTransitIndex), -1, 1),
              endLng,
              endLat);
    }

    JsonNode beforeBoardingRoute = walkingRoute(beforeBoarding);
    JsonNode afterAlightingRoute = walkingRoute(afterAlighting);
    ObjectNode mergedRoute = (ObjectNode) route.deepCopy();
    ArrayNode mergedSteps = JsonNodeFactory.instance.arrayNode();
    addWalkingSteps(mergedSteps, beforeBoardingRoute);
    steps.forEach(step -> mergedSteps.add(step.deepCopy()));
    addWalkingSteps(mergedSteps, afterAlightingRoute);
    mergedRoute.set("steps", mergedSteps);

    JsonNode properties = mergedRoute.get("properties");
    if (properties instanceof ObjectNode routeProperties) {
      routeProperties.put(
          "totalDistance",
          routeProperties.path("totalDistance").asInt()
              + routeDistance(beforeBoardingRoute)
              + routeDistance(afterAlightingRoute));
      routeProperties.put(
          "totalTime",
          routeProperties.path("totalTime").asInt()
              + routeTime(beforeBoardingRoute)
              + routeTime(afterAlightingRoute));
    }
    return mergedRoute;
  }

  private int firstTransitStep(JsonNode steps) {
    for (int index = 0; index < steps.size(); index++) {
      if (isTransitStep(steps.get(index))) {
        return index;
      }
    }
    return -1;
  }

  private int lastTransitStep(JsonNode steps) {
    for (int index = steps.size() - 1; index >= 0; index--) {
      if (isTransitStep(steps.get(index))) {
        return index;
      }
    }
    return -1;
  }

  private boolean isTransitStep(JsonNode step) {
    String type = step.path("properties").path("type").asText();
    return "BUS".equalsIgnoreCase(type) || "SUBWAY".equalsIgnoreCase(type);
  }

  private BigDecimal coordinate(JsonNode step, int pointIndex, int coordinateIndex) {
    JsonNode points = step.path("path").path("points");
    int resolvedPointIndex = pointIndex < 0 ? points.size() + pointIndex : pointIndex;
    return new BigDecimal(points.get(resolvedPointIndex).get(coordinateIndex).asText());
  }

  private void addWalkingSteps(ArrayNode target, JsonNode route) {
    if (route == null) {
      return;
    }
    for (JsonNode leg : route.path("legs")) {
      for (JsonNode step : leg.path("steps")) {
        ObjectNode walkingStep = (ObjectNode) step.deepCopy();
        ObjectNode properties = (ObjectNode) walkingStep.get("properties");
        properties.put("type", "WALKING");
        target.add(walkingStep);
      }
    }
  }

  private int routeDistance(JsonNode route) {
    return route == null ? 0 : route.path("properties").path("totalDistance").asInt();
  }

  private int routeTime(JsonNode route) {
    return route == null ? 0 : route.path("properties").path("totalTime").asInt();
  }

  private boolean isSuccessful(JsonNode response) {
    return response != null && "OK".equals(response.path("status").asText());
  }
}
