package com.taskflow.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinTeamRequest(
        @NotBlank(message = "inviteCode is required")
        @Size(max = 32, message = "inviteCode is too long")
        String inviteCode
) {
}
