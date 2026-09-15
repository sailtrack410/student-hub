ALTER TABLE work_order ADD COLUMN completion_photo_file_ids TEXT NULL;

UPDATE work_order
SET completion_photo_file_ids = CONCAT('[', completion_photo_file_id, ']')
WHERE completion_photo_file_id IS NOT NULL AND (completion_photo_file_ids IS NULL OR completion_photo_file_ids = '');
