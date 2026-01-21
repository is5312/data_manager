import React from 'react';
import {
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    SelectChangeEvent
} from '@mui/material';

export interface SchemaSelectorProps {
    selectedSchema: string;
    availableSchemas: string[];
    onSchemaChange: (schema: string) => void;
    disabled?: boolean;
}

export const SchemaSelector: React.FC<SchemaSelectorProps> = ({
    selectedSchema,
    availableSchemas,
    onSchemaChange,
    disabled = false
}) => {
    const handleChange = (event: SelectChangeEvent<string>) => {
        onSchemaChange(event.target.value);
    };

    return (
        <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel id="schema-selector-label">Schema</InputLabel>
            <Select
                labelId="schema-selector-label"
                id="schema-selector"
                value={selectedSchema}
                label="Schema"
                onChange={handleChange}
                disabled={disabled}
            >
                {availableSchemas.map((schema) => (
                    <MenuItem key={schema} value={schema}>
                        {schema}
                    </MenuItem>
                ))}
            </Select>
        </FormControl>
    );
};
