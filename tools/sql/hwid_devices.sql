CREATE TABLE hwid_devices (
    id INT AUTO_INCREMENT PRIMARY KEY,
    cpu VARCHAR(64) NOT NULL,
    hdd VARCHAR(64) NOT NULL,
    mac VARCHAR(64) NOT NULL,
    first_seen DATETIME,
    last_seen DATETIME,
    banned TINYINT(1) DEFAULT 0,
    UNIQUE KEY unique_hwid (cpu, hdd, mac)
);