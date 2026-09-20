package com.example.beyond_may_be.route.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.route.dto.RouteDtos;
import com.example.beyond_may_be.route.service.RouteService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/routes")
public class RouteController {

  private final RouteService routeService;

  @GetMapping
  public ApiResponse<RouteDtos.RouteResponse> getRoute(
      @RequestParam
          @DecimalMin(value = "-180.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "180.0", message = "_BAD_REQUEST")
          BigDecimal startLng,
      @RequestParam
          @DecimalMin(value = "-90.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "90.0", message = "_BAD_REQUEST")
          BigDecimal startLat,
      @RequestParam
          @DecimalMin(value = "-180.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "180.0", message = "_BAD_REQUEST")
          BigDecimal endLng,
      @RequestParam
          @DecimalMin(value = "-90.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "90.0", message = "_BAD_REQUEST")
          BigDecimal endLat) {
    return ApiResponse.onSuccess(routeService.getRoute(startLng, startLat, endLng, endLat));
  }
}
