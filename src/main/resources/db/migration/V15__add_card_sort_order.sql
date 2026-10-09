ALTER TABLE cards ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
MERGE INTO cards c
USING (
    SELECT id, CAST(ROW_NUMBER() OVER (PARTITION BY board_id, status ORDER BY id) - 1 AS INTEGER) AS position
    FROM cards
) ranked ON c.id = ranked.id
WHEN MATCHED THEN UPDATE SET sort_order = ranked.position;
ALTER TABLE cards ADD CONSTRAINT ck_cards_sort_order CHECK (sort_order >= 0);
CREATE INDEX idx_cards_board_status_order ON cards (board_id, status, sort_order, id);
