-- Create physical tables with proper tbl_<uuid> naming
CREATE TABLE tbl_a1b2c3d4 (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tbl_e5f6g7h8 (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tbl_i9j0k1l2 (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert metadata for Customers table
INSERT INTO base_reference_table (tbl_label, tbl_link, add_usr, add_ts) 
VALUES ('Customers', 'tbl_a1b2c3d4', 'system', CURRENT_TIMESTAMP);

-- Insert metadata for Orders table
INSERT INTO base_reference_table (tbl_label, tbl_link, add_usr, add_ts) 
VALUES ('Orders', 'tbl_e5f6g7h8', 'system', CURRENT_TIMESTAMP);

-- Insert metadata for Products table
INSERT INTO base_reference_table (tbl_label, tbl_link, add_usr, add_ts) 
VALUES ('Products', 'tbl_i9j0k1l2', 'system', CURRENT_TIMESTAMP);

-- Add columns to Customers physical table
ALTER TABLE tbl_a1b2c3d4 ADD COLUMN col_m3n4o5p6 VARCHAR(255);
ALTER TABLE tbl_a1b2c3d4 ADD COLUMN col_q7r8s9t0 VARCHAR(255);
ALTER TABLE tbl_a1b2c3d4 ADD COLUMN col_u1v2w3x4 VARCHAR(100);

-- Add columns to Orders physical table
ALTER TABLE tbl_e5f6g7h8 ADD COLUMN col_y5z6a7b8 INTEGER;
ALTER TABLE tbl_e5f6g7h8 ADD COLUMN col_c9d0e1f2 DECIMAL(10,2);
ALTER TABLE tbl_e5f6g7h8 ADD COLUMN col_g3h4i5j6 TIMESTAMP;

-- Add columns to Products physical table
ALTER TABLE tbl_i9j0k1l2 ADD COLUMN col_k7l8m9n0 VARCHAR(255);
ALTER TABLE tbl_i9j0k1l2 ADD COLUMN col_o1p2q3r4 TEXT;
ALTER TABLE tbl_i9j0k1l2 ADD COLUMN col_s5t6u7v8 DECIMAL(10,2);

-- Insert column metadata for Customers
INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_a1b2c3d4', 'Name', 'col_m3n4o5p6', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Customers';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_a1b2c3d4', 'Email', 'col_q7r8s9t0', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Customers';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_a1b2c3d4', 'Phone', 'col_u1v2w3x4', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Customers';

-- Insert column metadata for Orders
INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_e5f6g7h8', 'Customer ID', 'col_y5z6a7b8', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Orders';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_e5f6g7h8', 'Total Amount', 'col_c9d0e1f2', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Orders';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_e5f6g7h8', 'Order Date', 'col_g3h4i5j6', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Orders';

-- Insert column metadata for Products
INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_i9j0k1l2', 'Product Name', 'col_k7l8m9n0', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Products';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_i9j0k1l2', 'Description', 'col_o1p2q3r4', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Products';

INSERT INTO base_column_map (tbl_id, tbl_link, col_label, col_link, add_usr, add_ts)
SELECT id, 'tbl_i9j0k1l2', 'Price', 'col_s5t6u7v8', 'system', CURRENT_TIMESTAMP
FROM base_reference_table WHERE tbl_label = 'Products';
