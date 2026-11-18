package com.taskflow.team.dto;

import com.taskflow.team.TeamRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "role is required")
        TeamRole role
) {
}
