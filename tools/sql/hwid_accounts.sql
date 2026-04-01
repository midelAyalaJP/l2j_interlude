CREATE TABLE hwid_accounts (
    account_name VARCHAR(45) NOT NULL,
    device_id INT NOT NULL,
    first_seen DATETIME,
    last_seen DATETIME,
    PRIMARY KEY (account_name, device_id)
);