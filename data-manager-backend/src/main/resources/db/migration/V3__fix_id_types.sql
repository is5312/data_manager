-- Fix ID types to match JPA Long (BIGINT) expectation
-- Change base_reference_table ID
ALTER TABLE base_reference_table ALTER COLUMN id TYPE BIGINT;

-- Change base_column_map ID and foreign key
ALTER TABLE base_column_map ALTER COLUMN id TYPE BIGINT;
ALTER TABLE base_column_map ALTER COLUMN tbl_id TYPE BIGINT;
