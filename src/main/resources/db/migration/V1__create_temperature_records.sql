CREATE TABLE IF NOT EXISTS temperature_records (
    name VARCHAR(255) NOT NULL,
    datetime DATETIME NOT NULL,
    temp DECIMAL(5,1) NOT NULL,
    CONSTRAINT COMPOSITE_UNIQUE UNIQUE (name, datetime)
);
