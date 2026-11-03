ALTER TABLE items ADD COLUMN deletion_pending INTEGER NOT NULL DEFAULT 0
  CHECK (deletion_pending IN (0, 1));
