ALTER TABLE email ALTER COLUMN from_address CLOB;
ALTER TABLE email ALTER COLUMN to_address CLOB;
ALTER TABLE email ALTER COLUMN message_id CLOB;

ALTER TABLE email_attachment ALTER COLUMN filename CLOB;
ALTER TABLE email_attachment ALTER COLUMN processing_message CLOB;

ALTER TABLE email_inline_image ALTER COLUMN content_id CLOB;
ALTER TABLE email_inline_image ALTER COLUMN content_type CLOB;
ALTER TABLE email_inline_image ALTER COLUMN processing_message CLOB;

ALTER TABLE email_content ALTER COLUMN processing_message CLOB;
