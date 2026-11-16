CREATE DATABASE IF NOT EXISTS campus_facility_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_facility_db;

-- One row per booking. Rates and totals are saved as requested by the assignment.
CREATE TABLE IF NOT EXISTS reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL,
    user_role ENUM('STUDENT', 'FACULTY') NOT NULL,
    item_category ENUM('LAPTOP', 'PROJECTOR', 'POD') NOT NULL,
    quantity INT NOT NULL,
    booking_days INT NOT NULL,
    daily_rate DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    net_total DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (quantity > 0),
    CHECK (booking_days > 0),
    CHECK (daily_rate >= 0),
    CHECK (subtotal >= 0),
    CHECK (net_total >= 0)
);
