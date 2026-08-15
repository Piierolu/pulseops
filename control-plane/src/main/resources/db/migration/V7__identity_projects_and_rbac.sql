CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(512) NOT NULL,
    email VARCHAR(320),
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_users_issuer_subject UNIQUE (issuer, subject)
);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE team_memberships (
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX idx_team_memberships_user ON team_memberships (user_id, team_id);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_projects_team_slug UNIQUE (team_id, slug)
);

CREATE INDEX idx_projects_team ON projects (team_id, created_at DESC);

INSERT INTO teams (id, name, slug, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Legacy', 'legacy', now(), now());

INSERT INTO projects (id, team_id, name, slug, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Default',
    'default',
    now(),
    now()
);

ALTER TABLE monitors
    ADD COLUMN project_id UUID,
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE monitors
SET project_id = '00000000-0000-0000-0000-000000000002';

ALTER TABLE monitors
    ALTER COLUMN project_id SET NOT NULL,
    ADD CONSTRAINT fk_monitors_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE RESTRICT;

CREATE INDEX idx_monitors_project_id ON monitors (project_id);

CREATE INDEX idx_monitors_project_created
    ON monitors (project_id, created_at DESC)
    WHERE archived_at IS NULL;

CREATE INDEX idx_monitors_active_scheduling
    ON monitors (enabled, id)
    WHERE archived_at IS NULL;
