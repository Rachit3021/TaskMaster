package com.taskflow.user.dto;

import com.taskflow.user.User;

import java.util.UUID;

/** Lightweight user representation embedded in task, comment and team payloads. */
public record UserSummary(UUID id, String name, String email, String avatarUrl) {
    public static UserSummary from(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getAvatarUrl());
    }
}
