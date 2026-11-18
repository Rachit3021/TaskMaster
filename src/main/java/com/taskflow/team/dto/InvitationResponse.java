package com.taskflow.team.dto;

import com.taskflow.team.InvitationStatus;
import com.taskflow.team.TeamRole;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

/** Invitation payload; {@code token} is returned to team managers so it can be shared. */
public record InvitationResponse(
        UUID id,
        UUID teamId,
        String teamName,
        String email,
        TeamRole role,
        InvitationStatus status,
        String token,
        UserSummary invitedBy,
        Instant expiresAt,
        Instant createdAt
) {
}
