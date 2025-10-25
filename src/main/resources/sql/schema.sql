-- DROP TABLE IF EXISTS users;
-- DROP TABLE IF EXISTS foods;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    encrypted_password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    is_authorized BOOLEAN DEFAULT FALSE,
    refresh_token VARCHAR(1000),
    email VARCHAR(255) UNIQUE
);

CREATE TABLE IF NOT EXISTS foods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    coefficient DOUBLE,
    carb_grams DOUBLE,
    quantity DOUBLE,
    unit VARCHAR(10),
    notes VARCHAR(100),
    image BLOB,
    image_content_type VARCHAR(100),
    is_favorite BOOLEAN DEFAULT FALSE
);

-- Remember Me token 表
CREATE TABLE IF NOT EXISTS persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);