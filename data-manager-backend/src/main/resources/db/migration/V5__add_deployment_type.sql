-- Flyway Migration V5: Add deployment_type to base_reference_table
-- Adds deployment type field to distinguish between RUN_TIME and DESIGN_TIME tables

-- Add deployment_type column to base_reference_table
ALTER TABLE base_reference_table 
ADD COLUMN IF NOT EXISTS deployment_type VARCHAR(20) DEFAULT 'DESIGN_TIME';

-- Update existing records to have deployment_type = 'DESIGN_TIME' if NULL
UPDATE base_reference_table SET deployment_type = 'DESIGN_TIME' WHERE deployment_type IS NULL;

-- Add check constraint to ensure only valid values
ALTER TABLE base_reference_table 
ADD CONSTRAINT check_deployment_type 
CHECK (deployment_type IN ('RUN_TIME', 'DESIGN_TIME'));
