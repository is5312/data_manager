-- Flyway Migration V4: Add description and version_no to metadata tables
-- Adds version tracking and description fields to base_reference_table and base_column_map

-- Add columns to base_reference_table
ALTER TABLE base_reference_table 
ADD COLUMN IF NOT EXISTS description TEXT,
ADD COLUMN IF NOT EXISTS version_no INTEGER DEFAULT 1;

-- Add columns to base_column_map
ALTER TABLE base_column_map
ADD COLUMN IF NOT EXISTS description TEXT,
ADD COLUMN IF NOT EXISTS version_no INTEGER DEFAULT 1;

-- Update existing records to have version_no = 1 if NULL
UPDATE base_reference_table SET version_no = 1 WHERE version_no IS NULL;
UPDATE base_column_map SET version_no = 1 WHERE version_no IS NULL;
