ALTER TABLE projects ADD COLUMN user_id UUID;

UPDATE projects
SET user_id = (
    SELECT members.user_id
    FROM members
    WHERE members.project_id = projects.id
      AND members.role = 'OWNER'
);

ALTER TABLE projects ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE projects
    ADD CONSTRAINT uk_projects_user_code UNIQUE (user_id, code);
