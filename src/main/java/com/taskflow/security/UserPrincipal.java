package com.taskflow.security;

import com.taskflow.user.Role;

import java.util.UUID;

/** Authentication principal stored in the Spring Security context. */
public record UserPrincipal(UUID id, String email, String name, Role role) {
}
