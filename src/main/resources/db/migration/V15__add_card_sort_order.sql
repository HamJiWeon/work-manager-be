ALTER TABLE cards ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
UPDATE cards SET sort_order = (
    SELECT CAST(COUNT(*) AS INTEGER) FROM cards previous
    WHERE previous.board_id = cards.board_id AND previous.status = cards.status AND previous.id < cards.id
);
ALTER TABLE cards ADD CONSTRAINT ck_cards_sort_order CHECK (sort_order >= 0);
CREATE INDEX idx_cards_board_status_order ON cards (board_id, status, sort_order, id);
