package com.example.beyond_may_be.team.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersRequest;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersResponse;
import com.example.beyond_may_be.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamController {
  private final TeamService teamService;

  @PostMapping("/{teamId}/max-members")
  public ApiResponse<MaxMembersResponse> setMaxMembers(
      @PathVariable Long teamId, @RequestBody MaxMembersRequest request) {
    return ApiResponse.onSuccess(teamService.setMaxMembers(teamId, request));
  }
}
