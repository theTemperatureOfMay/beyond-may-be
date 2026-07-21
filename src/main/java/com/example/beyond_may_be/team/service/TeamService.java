package com.example.beyond_may_be.team.service;

import com.example.beyond_may_be.team.converter.TeamConverter;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersRequest;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersResponse;
import org.springframework.stereotype.Service;

@Service
public class TeamService {
  public MaxMembersResponse setMaxMembers(Long teamId, MaxMembersRequest request) {
    return TeamConverter.toMaxMembersResponse(teamId, request.maxMembers());
  }
}
