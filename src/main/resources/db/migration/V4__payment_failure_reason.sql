ALTER TABLE payment
    ADD COLUMN failure_code    VARCHAR(50)  NULL AFTER status,
    ADD COLUMN failure_message VARCHAR(500) NULL AFTER failure_code;
