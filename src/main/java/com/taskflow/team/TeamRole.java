package com.taskflow.team;

public enum TeamRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }
}
