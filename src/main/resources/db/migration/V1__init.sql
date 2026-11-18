-- TaskFlow initial schema (PostgreSQL)
-- Mirrors the JPA entities. Hibernate runs in `validate` mode against this schema,
-- so any entity change must ship with a new migration.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    bio           VARCHAR(500),
    avatar_url    VARCHAR(500),
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN      NOT NULL,
    user_agent  VARCHAR(255),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE teams (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    invite_code VARCHAR(16)  NOT NULL,
    owner_id    UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_teams_invite_code UNIQUE (invite_code)
);

CREATE TABLE team_members (
    id        UUID PRIMARY KEY,
    team_id   UUID        NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_team_members_team_user UNIQUE (team_id, user_id)
);
CREATE INDEX idx_team_members_team ON team_members (team_id);
CREATE INDEX idx_team_members_user ON team_members (user_id);

CREATE TABLE team_invitations (
    id          UUID PRIMARY KEY,
    team_id     UUID         NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    token       VARCHAR(64)  NOT NULL,
    invited_by  UUID         NOT NULL REFERENCES users (id),
    status      VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP(6) WITH TIME ZONE,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_team_invitations_token UNIQUE (token)
);
CREATE INDEX idx_team_invitations_team ON team_invitations (team_id);
CREATE INDEX idx_team_invitations_email ON team_invitations (email);

CREATE TABLE projects (
    id          UUID PRIMARY KEY,
    team_id     UUID         NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    name        VARCHAR(160) NOT NULL,
    description VARCHAR(4000),
    status      VARCHAR(20)  NOT NULL,
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_projects_team ON projects (team_id);
CREATE INDEX idx_projects_status ON projects (status);

CREATE TABLE tasks (
    id           UUID PRIMARY KEY,
    project_id   UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title        VARCHAR(160) NOT NULL,
    description  VARCHAR(5000),
    status       VARCHAR(20)  NOT NULL,
    priority     VARCHAR(20)  NOT NULL,
    due_date     DATE,
    assignee_id  UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_by   UUID         NOT NULL REFERENCES users (id),
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_tasks_project ON tasks (project_id);
CREATE INDEX idx_tasks_assignee ON tasks (assignee_id);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);

CREATE TABLE task_labels (
    task_id UUID        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    label   VARCHAR(40) NOT NULL,
    PRIMARY KEY (task_id, label)
);

CREATE TABLE comments (
    id         UUID PRIMARY KEY,
    task_id    UUID          NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id),
    body       VARCHAR(4000) NOT NULL,
    edited     BOOLEAN       NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_comments_task ON comments (task_id);

CREATE TABLE attachments (
    id            UUID PRIMARY KEY,
    task_id       UUID         NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    uploaded_by   UUID         NOT NULL REFERENCES users (id),
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(150) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_attachments_stored_name UNIQUE (stored_name)
);
CREATE INDEX idx_attachments_task ON attachments (task_id);

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(40)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    team_id      UUID,
    project_id   UUID,
    task_id      UUID,
    read_at      TIMESTAMP(6) WITH TIME ZONE,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, read_at);
