CREATE TABLE temperature_reading (
    name VARCHAR(255) NOT NULL,
    recorded_at DATETIME NOT NULL,
    temperature DOUBLE NOT NULL,
    UNIQUE KEY uk_name_recorded_at (name, recorded_at)
);
