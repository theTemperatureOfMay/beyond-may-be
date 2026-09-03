package com.example.beyond_may_be.visit.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.apiPayload.code.status.SuccessStatus;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.service.VisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/visits")
public class VisitController {

  private final VisitService visitService;

  @PostMapping
  public ResponseEntity<ApiResponse<VisitDtos.ConfirmResponse>> confirm(
      @Valid @RequestBody VisitDtos.ConfirmRequest request, @AuthenticationPrincipal Long userId) {
    return ResponseEntity.status(SuccessStatus._CREATED.getHttpStatus())
        .body(ApiResponse.of(SuccessStatus._CREATED, visitService.confirmVisit(request, userId)));
  }

  @PostMapping(value = "/{visitId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<VisitDtos.PhotoResponse>> attachPhoto(
      @PathVariable Long visitId,
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.status(SuccessStatus._CREATED.getHttpStatus())
        .body(
            ApiResponse.of(
                SuccessStatus._CREATED, visitService.attachPhoto(visitId, file, userId)));
  }
}
