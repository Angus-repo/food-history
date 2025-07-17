-- DROP TABLE IF EXISTS users;
-- DROP TABLE IF EXISTS foods;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    encrypted_password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    is_authorized BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS foods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    serving_size VARCHAR(10),
    carb_grams INTEGER,
    quantity DOUBLE,
    unit VARCHAR(10),
    notes VARCHAR(100),
    image BLOB,
    image_content_type VARCHAR(100),
    is_favorite BOOLEAN DEFAULT FALSE
);