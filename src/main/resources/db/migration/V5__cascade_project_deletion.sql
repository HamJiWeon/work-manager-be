ALTER TABLE cards
    DROP CONSTRAINT fk_cards_project;

ALTER TABLE cards
    ADD CONSTRAINT fk_cards_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE;

ALTER TABLE boards
    DROP CONSTRAINT fk_boards_project;

ALTER TABLE boards
    ADD CONSTRAINT fk_boards_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE;

ALTER TABLE workspaces
    DROP CONSTRAINT fk_workspaces_project;

ALTER TABLE workspaces
    ADD CONSTRAINT fk_workspaces_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE;

ALTER TABLE members
    DROP CONSTRAINT fk_members_project;

ALTER TABLE members
    ADD CONSTRAINT fk_members_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE;
