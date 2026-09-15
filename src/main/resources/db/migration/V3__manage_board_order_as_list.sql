CREATE TABLE board_order_migration AS
SELECT id, CAST(ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY sort_order, id) - 1 AS INTEGER) AS position
FROM boards;

UPDATE boards SET sort_order = (SELECT position FROM board_order_migration WHERE board_order_migration.id = boards.id);

DROP TABLE board_order_migration;

ALTER TABLE boards ALTER COLUMN sort_order SET DEFAULT 0;
