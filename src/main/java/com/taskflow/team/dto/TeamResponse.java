package com.taskflow.team.dto;

import com.taskflow.team.TeamRole;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * Team payload. {@code inviteCode} is only populated for OWNER/ADMIN callers so
 * a regular member cannot re-share the code.
 */
public record TeamResponse(
        UUID id,
        String name,
        String description,
        String inviteCode,
        UserSummary owner,
        TeamRole myRole,
        long memberCount,
        long projectCount,
        Instant createdAt,
        Instant updatedAt
) {
}
