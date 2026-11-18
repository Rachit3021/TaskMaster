package com.taskflow.user.dto;

import com.taskflow.user.Role;
import com.taskflow.user.User;

import java.time.Instant;
import java.util.UUID;

/** Full representation of the authenticated user. */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String bio,
        String avatarUrl,
        Role role,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
