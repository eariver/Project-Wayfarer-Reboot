-- Project Wayfarer Reboot — shared MariaDB setup (run once, as a DBA/root).
-- The repository intentionally stores NO credentials. The runtime renders them from
-- a gitignored properties file (see docs/deploy/README.md).
CREATE DATABASE IF NOT EXISTS wayfarer_luckperms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS wayfarer_mcmmo    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Optional (EvenMoreFish production parity):
-- CREATE DATABASE IF NOT EXISTS wayfarer_evenmorefish CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'wayfarer'@'127.0.0.1' IDENTIFIED BY '<RUNTIME_PASSWORD>';
CREATE USER IF NOT EXISTS 'wayfarer'@'localhost' IDENTIFIED BY '<RUNTIME_PASSWORD>';

GRANT ALL PRIVILEGES ON wayfarer_luckperms.* TO 'wayfarer'@'127.0.0.1';
GRANT ALL PRIVILEGES ON wayfarer_mcmmo.*    TO 'wayfarer'@'127.0.0.1';
GRANT ALL PRIVILEGES ON wayfarer_luckperms.* TO 'wayfarer'@'localhost';
GRANT ALL PRIVILEGES ON wayfarer_mcmmo.*    TO 'wayfarer'@'localhost';
FLUSH PRIVILEGES;
