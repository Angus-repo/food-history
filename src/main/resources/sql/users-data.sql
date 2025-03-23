MERGE INTO users AS target
USING (SELECT 'admin' AS username, '$2a$10$6Psg3WyPoqSRwBpUDjzjl.o/STABWJQaOoU7HBpjBMTmZJnlwzJMS' AS encrypted_password, 'USER' AS role, true AS enabled) AS source
ON (target.username = source.username)
WHEN NOT MATCHED THEN
    INSERT (username, encrypted_password, role, enabled)
    VALUES (source.username, source.encrypted_password, source.role, source.enabled);