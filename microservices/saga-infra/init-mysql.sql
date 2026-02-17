-- =============================================================================
-- MySQL init script: Create databases for Database-per-Service (Saga)
-- =============================================================================
-- Mount this as /docker-entrypoint-initdb.d/init-mysql.sql in MySQL container.
-- Creates: order_db, kitchen_db, accounting_db (saga_user has access).
-- =============================================================================

CREATE DATABASE IF NOT EXISTS order_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS kitchen_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS accounting_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON order_db.* TO 'saga_user'@'%';
GRANT ALL PRIVILEGES ON kitchen_db.* TO 'saga_user'@'%';
GRANT ALL PRIVILEGES ON accounting_db.* TO 'saga_user'@'%';
FLUSH PRIVILEGES;
