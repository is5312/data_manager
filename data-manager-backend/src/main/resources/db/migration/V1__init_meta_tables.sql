CREATE TABLE IF NOT EXISTS base_reference_table (
    id SERIAL PRIMARY KEY,
    tbl_label VARCHAR(255) NOT NULL,
    tbl_link VARCHAR(255) NOT NULL UNIQUE,
    add_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_usr VARCHAR(255),
    upd_ts TIMESTAMP,
    upd_usr VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS base_column_map (
    id SERIAL PRIMARY KEY,
    tbl_id INTEGER REFERENCES base_reference_table(id),
    tbl_link VARCHAR(255) NOT NULL,
    col_label VARCHAR(255) NOT NULL,
    col_link VARCHAR(255) NOT NULL,
    add_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_usr VARCHAR(255),
    upd_ts TIMESTAMP,
    upd_usr VARCHAR(255)
);
