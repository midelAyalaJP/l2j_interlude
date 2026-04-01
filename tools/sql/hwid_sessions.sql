CREATE TABLE hwid_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_name VARCHAR(45),
    device_id INT,
    ip_address VARCHAR(45),
    token VARCHAR(64),
    login_time DATETIME,
    last_heartbeat DATETIME,
    active TINYINT(1) DEFAULT 1
);