CREATE UNIQUE INDEX uk_workspaces_personal_created_by_active
    ON workspaces (created_by)
    WHERE deleted_at IS NULL AND type = 'PERSONAL';
