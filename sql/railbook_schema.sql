-- ============================================================
-- RAILBOOK DATABASE SCHEMA
-- Combined OOP + RDBMS College Project
-- ============================================================

-- Create database (if not already created)
CREATE DATABASE IF NOT EXISTS railbook;
USE railbook;

-- ============================================================
-- ORIGINAL TABLES (already in the project)
-- ============================================================

-- trains table
CREATE TABLE IF NOT EXISTS trains (
    train_number INT PRIMARY KEY,
    train_name   VARCHAR(100) NOT NULL,
    source       VARCHAR(100) NOT NULL,
    destination  VARCHAR(100) NOT NULL,
    total_seats  INT NOT NULL,
    base_fare    DECIMAL(10,2) NOT NULL,
    train_type   ENUM('EXPRESS','LOCAL') NOT NULL DEFAULT 'LOCAL'
);

-- passengers table
CREATE TABLE IF NOT EXISTS passengers (
    passenger_id INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    age          INT NOT NULL CHECK (age BETWEEN 1 AND 120),
    gender       CHAR(1) NOT NULL CHECK (gender IN ('M','F','O'))
);

-- tickets table
CREATE TABLE IF NOT EXISTS tickets (
    pnr           VARCHAR(20) PRIMARY KEY,
    train_number  INT NOT NULL,
    passenger_id  INT NOT NULL,
    seat_number   INT,
    status        ENUM('CONFIRMED','WAITING','CANCELLED') NOT NULL DEFAULT 'WAITING',
    fare          DECIMAL(10,2) NOT NULL,
    booked_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (train_number) REFERENCES trains(train_number),
    FOREIGN KEY (passenger_id) REFERENCES passengers(passenger_id)
);

-- Sample train data (if not present)
INSERT IGNORE INTO trains VALUES
(12401, 'Rajdhani Express',   'Delhi',   'Mumbai',  5, 1200.00, 'EXPRESS'),
(12402, 'Shatabdi Express',   'Jaipur',  'Delhi',   5, 800.00,  'EXPRESS'),
(12403, 'Garib Rath',         'Delhi',   'Jaipur',  5, 400.00,  'LOCAL'),
(12404, 'Duronto Express',    'Mumbai',  'Delhi',   5, 1500.00, 'EXPRESS'),
(12405, 'Jan Shatabdi',       'Jaipur',  'Mumbai',  5, 600.00,  'LOCAL');


-- ============================================================
-- NEW TABLE 1: USER PROFILES (Personal Details)
-- ============================================================

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(15)  NOT NULL,
    date_of_birth DATE,
    address       VARCHAR(255),
    city          VARCHAR(50),
    state         VARCHAR(50),
    pincode       VARCHAR(10),
    id_proof_type ENUM('AADHAR','PAN','PASSPORT','VOTER_ID') DEFAULT 'AADHAR',
    id_proof_no   VARCHAR(50),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


-- ============================================================
-- NEW TABLE 2: TRANSACTIONS (Payment History)
-- ============================================================

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id  INT AUTO_INCREMENT PRIMARY KEY,
    pnr             VARCHAR(20) NOT NULL,
    user_id         INT,
    amount          DECIMAL(10,2) NOT NULL,
    payment_mode    ENUM('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET') NOT NULL DEFAULT 'UPI',
    transaction_type ENUM('BOOKING','CANCELLATION_REFUND') NOT NULL DEFAULT 'BOOKING',
    status          ENUM('SUCCESS','FAILED','PENDING') NOT NULL DEFAULT 'SUCCESS',
    transaction_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pnr) REFERENCES tickets(pnr),
    FOREIGN KEY (user_id) REFERENCES user_profiles(user_id)
);


-- ============================================================
-- INDEXES for performance
-- ============================================================

CREATE INDEX idx_tickets_train    ON tickets(train_number);
CREATE INDEX idx_tickets_status   ON tickets(status);
CREATE INDEX idx_tickets_booked   ON tickets(booked_at);
CREATE INDEX idx_trans_user       ON transactions(user_id);
CREATE INDEX idx_trans_pnr        ON transactions(pnr);
CREATE INDEX idx_trans_status     ON transactions(status);
CREATE INDEX idx_profile_username ON user_profiles(username);


-- ============================================================
-- VIEWS (useful for viva — demonstrates SQL views)
-- ============================================================

-- View 1: Booking history with passenger + train details
CREATE OR REPLACE VIEW v_booking_history AS
SELECT 
    t.pnr,
    p.name AS passenger_name,
    p.age,
    p.gender,
    tr.train_name,
    tr.train_number,
    tr.source,
    tr.destination,
    tr.train_type,
    t.seat_number,
    t.status,
    t.fare,
    t.booked_at
FROM tickets t
JOIN passengers p ON t.passenger_id = p.passenger_id
JOIN trains tr ON t.train_number = tr.train_number
ORDER BY t.booked_at DESC;

-- View 2: Transaction history with user details
CREATE OR REPLACE VIEW v_transaction_history AS
SELECT 
    tx.transaction_id,
    tx.pnr,
    u.full_name AS user_name,
    u.email,
    tx.amount,
    tx.payment_mode,
    tx.transaction_type,
    tx.status,
    tx.transaction_at
FROM transactions tx
LEFT JOIN user_profiles u ON tx.user_id = u.user_id
ORDER BY tx.transaction_at DESC;

-- View 3: Currently travelling passengers (confirmed tickets)
CREATE OR REPLACE VIEW v_travelling_passengers AS
SELECT 
    t.pnr,
    p.name,
    p.age,
    p.gender,
    tr.train_name,
    tr.train_number,
    tr.source,
    tr.destination,
    t.seat_number,
    t.fare
FROM tickets t
JOIN passengers p ON t.passenger_id = p.passenger_id
JOIN trains tr ON t.train_number = tr.train_number
WHERE t.status = 'CONFIRMED'
ORDER BY tr.train_number, t.seat_number;

-- View 4: Revenue report per train
CREATE OR REPLACE VIEW v_revenue_per_train AS
SELECT 
    tr.train_number,
    tr.train_name,
    COUNT(CASE WHEN t.status = 'CONFIRMED' THEN 1 END)  AS confirmed_count,
    COUNT(CASE WHEN t.status = 'WAITING' THEN 1 END)    AS waiting_count,
    COUNT(CASE WHEN t.status = 'CANCELLED' THEN 1 END)  AS cancelled_count,
    SUM(CASE WHEN t.status = 'CONFIRMED' THEN t.fare ELSE 0 END) AS total_revenue
FROM trains tr
LEFT JOIN tickets t ON tr.train_number = t.train_number
GROUP BY tr.train_number, tr.train_name
ORDER BY total_revenue DESC;


-- ============================================================
-- TRIGGER: Auto-create a transaction when a ticket is booked
-- ============================================================

DELIMITER //
CREATE TRIGGER trg_after_ticket_insert
AFTER INSERT ON tickets
FOR EACH ROW
BEGIN
    INSERT INTO transactions (pnr, amount, payment_mode, transaction_type, status)
    VALUES (NEW.pnr, NEW.fare, 'UPI', 'BOOKING', 'SUCCESS');
END //
DELIMITER ;

-- TRIGGER: Auto-create refund transaction when ticket is cancelled
DELIMITER //
CREATE TRIGGER trg_after_ticket_cancel
AFTER UPDATE ON tickets
FOR EACH ROW
BEGIN
    IF OLD.status != 'CANCELLED' AND NEW.status = 'CANCELLED' THEN
        INSERT INTO transactions (pnr, amount, payment_mode, transaction_type, status)
        VALUES (NEW.pnr, NEW.fare, 'UPI', 'CANCELLATION_REFUND', 'SUCCESS');
    END IF;
END //
DELIMITER ;


-- ============================================================
-- DML OPERATIONS (CRUD examples for viva)
-- ============================================================

-- INSERT: Add a new user profile
INSERT INTO user_profiles (username, full_name, email, phone, date_of_birth, address, city, state, pincode, id_proof_type, id_proof_no)
VALUES ('johndoe', 'John Doe', 'john@example.com', '9876543210', '2000-05-15', '42 Park Avenue', 'Delhi', 'Delhi', '110001', 'AADHAR', '1234-5678-9012');

-- SELECT: Get user profile by username
SELECT * FROM user_profiles WHERE username = 'johndoe';

-- UPDATE: Modify user email and phone
UPDATE user_profiles 
SET email = 'john.doe@newmail.com', phone = '9999999999'
WHERE username = 'johndoe';

-- DELETE: Remove a user profile
-- DELETE FROM user_profiles WHERE username = 'johndoe';

-- SELECT with JOIN: Get all bookings for a user
SELECT u.full_name, u.email, t.pnr, t.status, t.fare, tr.train_name
FROM user_profiles u
JOIN transactions tx ON u.user_id = tx.user_id
JOIN tickets t ON tx.pnr = t.pnr
JOIN trains tr ON t.train_number = tr.train_number
WHERE u.username = 'johndoe';

-- AGGREGATE: Revenue summary grouped by payment mode
SELECT payment_mode, 
       COUNT(*) AS total_transactions,
       SUM(amount) AS total_amount,
       AVG(amount) AS avg_amount
FROM transactions 
WHERE status = 'SUCCESS'
GROUP BY payment_mode
HAVING total_amount > 0
ORDER BY total_amount DESC;

-- SUBQUERY: Find users who have booked express trains
SELECT full_name, email FROM user_profiles 
WHERE user_id IN (
    SELECT DISTINCT tx.user_id FROM transactions tx
    JOIN tickets t ON tx.pnr = t.pnr
    JOIN trains tr ON t.train_number = tr.train_number
    WHERE tr.train_type = 'EXPRESS'
);

-- ALTER TABLE: Add a loyalty_points column
ALTER TABLE user_profiles ADD COLUMN loyalty_points INT DEFAULT 0;

-- UPDATE with subquery: Award 100 points for each confirmed booking
UPDATE user_profiles u
SET loyalty_points = (
    SELECT COUNT(*) * 100 FROM transactions tx
    JOIN tickets t ON tx.pnr = t.pnr
    WHERE tx.user_id = u.user_id AND t.status = 'CONFIRMED'
);

-- GRANT (for multi-user setup — explain in viva)
-- GRANT SELECT, INSERT ON railbook.* TO 'app_user'@'localhost';

-- REVOKE
-- REVOKE INSERT ON railbook.* FROM 'app_user'@'localhost';


-- ============================================================
-- STORED PROCEDURE: Get booking summary for a user
-- ============================================================

DELIMITER //
CREATE PROCEDURE sp_user_booking_summary(IN p_username VARCHAR(50))
BEGIN
    SELECT 
        u.full_name,
        COUNT(DISTINCT tx.pnr) AS total_bookings,
        SUM(CASE WHEN tx.transaction_type = 'BOOKING' THEN tx.amount ELSE 0 END) AS total_spent,
        SUM(CASE WHEN tx.transaction_type = 'CANCELLATION_REFUND' THEN tx.amount ELSE 0 END) AS total_refunded
    FROM user_profiles u
    LEFT JOIN transactions tx ON u.user_id = tx.user_id
    WHERE u.username = p_username
    GROUP BY u.full_name;
END //
DELIMITER ;

-- CALL sp_user_booking_summary('johndoe');
