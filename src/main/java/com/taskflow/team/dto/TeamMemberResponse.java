package com.taskflow.team.dto;

import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamRole;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        UserSummary user,
        TeamRole role,
        Instant joinedAt
) {
    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(
                member.getId(),
                UserSummary.from(member.getUser()),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
