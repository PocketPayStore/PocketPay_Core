ALTER TABLE point_ledger
    DROP CHECK ck_point_ledger_type,
    ADD CONSTRAINT ck_point_ledger_type CHECK (type IN ('EARN', 'USE', 'CANCEL_RESTORE', 'EARN_REVERSAL'));
