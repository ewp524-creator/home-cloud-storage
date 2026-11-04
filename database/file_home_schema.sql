-- File Home database schema
-- Target: MySQL 8.0+
--
-- Third normal form notes:
--   1. file_entry stores hierarchy metadata only; physical object metadata is in file_object.
--   2. Folder paths, item counts, active share status and access counters are derived, not stored.
--   3. Tags are many-to-many through file_tag.
--   4. User-specific starring is stored in user_file_state.
--   5. Folder sharing uses root_entry_id + is_recursive instead of copied child snapshots.
--   6. Public share URLs contain a random token; the database stores only its SHA-256 hash.

CREATE DATABASE IF NOT EXISTS file_home
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE file_home;

CREATE TABLE IF NOT EXISTS user_account (
  id CHAR(36) NOT NULL,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  avatar_url VARCHAR(512) NULL,
  storage_quota_bytes BIGINT UNSIGNED NOT NULL DEFAULT 10737418240,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  deactivated_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_account_username (username),
  UNIQUE KEY uk_user_account_email (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_preference (
  user_id CHAR(36) NOT NULL,
  default_view ENUM('GRID', 'LIST') NOT NULL DEFAULT 'GRID',
  default_sort VARCHAR(32) NOT NULL DEFAULT 'modified-desc',
  reduce_motion TINYINT(1) NOT NULL DEFAULT 0,
  upload_concurrency TINYINT UNSIGNED NOT NULL DEFAULT 4,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_preference_user
    FOREIGN KEY (user_id) REFERENCES user_account (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS auth_refresh_token (
  id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  token_hash BINARY(32) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
  KEY idx_auth_refresh_token_user (user_id, expires_at),
  CONSTRAINT fk_auth_refresh_token_user
    FOREIGN KEY (user_id) REFERENCES user_account (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS auth_session (
  id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  token_hash BINARY(32) NOT NULL,
  user_agent VARCHAR(512) NULL,
  created_ip_hash BINARY(32) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_session_token_hash (token_hash),
  KEY idx_auth_session_user_active (user_id, revoked_at, expires_at),
  KEY idx_auth_session_expiry (expires_at, revoked_at),
  CONSTRAINT fk_auth_session_user
    FOREIGN KEY (user_id) REFERENCES user_account (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS file_entry (
  id CHAR(36) NOT NULL,
  owner_id CHAR(36) NOT NULL,
  parent_id CHAR(36) NULL,
  entry_kind ENUM('FILE', 'FOLDER') NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,
  active_name VARCHAR(255)
    GENERATED ALWAYS AS (IF(deleted_at IS NULL, name, NULL)) STORED,
  parent_scope_id CHAR(36)
    GENERATED ALWAYS AS (
      IFNULL(parent_id, '00000000-0000-0000-0000-000000000000')
    ) STORED,
  root_scope_id CHAR(36)
    GENERATED ALWAYS AS (IF(parent_id IS NULL, owner_id, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_entry_active_name
    (owner_id, parent_scope_id, active_name),
  UNIQUE KEY uk_file_entry_root (root_scope_id),
  KEY idx_file_entry_parent (owner_id, parent_id, deleted_at),
    KEY idx_file_entry_page
        (owner_id, parent_id, deleted_at, updated_at, id),
  KEY idx_file_entry_recent (owner_id, updated_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS file_object (
  entry_id CHAR(36) NOT NULL,
  storage_bucket VARCHAR(63) NOT NULL,
  storage_object_key VARCHAR(512) NOT NULL,
  storage_version_id VARCHAR(255) NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  mime_type VARCHAR(255) NOT NULL,
  checksum_sha256 BINARY(32) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (entry_id),
  UNIQUE KEY uk_file_object_storage_key (storage_bucket, storage_object_key),
  CONSTRAINT fk_file_object_entry
    FOREIGN KEY (entry_id) REFERENCES file_entry (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_file_state (
  user_id CHAR(36) NOT NULL,
  entry_id CHAR(36) NOT NULL,
  is_starred TINYINT(1) NOT NULL DEFAULT 0,
  last_accessed_at DATETIME(6) NULL,
  PRIMARY KEY (user_id, entry_id),
  KEY idx_user_file_state_starred (user_id, is_starred),
  CONSTRAINT fk_user_file_state_user
    FOREIGN KEY (user_id) REFERENCES user_account (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_user_file_state_entry
    FOREIGN KEY (entry_id) REFERENCES file_entry (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS upload_session (
  id CHAR(36) NOT NULL,
  owner_id CHAR(36) NOT NULL,
  target_entry_id CHAR(36) NOT NULL,
  parent_id CHAR(36) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(255) NOT NULL,
  client_fingerprint CHAR(64) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  part_size_bytes BIGINT UNSIGNED NOT NULL,
  total_parts INT UNSIGNED NOT NULL,
  storage_bucket VARCHAR(63) NOT NULL,
  storage_object_key VARCHAR(512) NOT NULL,
  minio_upload_id VARCHAR(255) NOT NULL,
  status ENUM(
    'INITIATED',
    'UPLOADING',
    'COMPLETING',
    'COMPLETED',
    'ABORTED'
  )
    NOT NULL DEFAULT 'INITIATED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_upload_session_target (target_entry_id),
  KEY idx_upload_session_owner_active
    (owner_id, status, expires_at, updated_at),
  KEY idx_upload_session_expiry (status, expires_at),
  CONSTRAINT fk_upload_session_owner
    FOREIGN KEY (owner_id) REFERENCES user_account (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_upload_session_parent
    FOREIGN KEY (parent_id) REFERENCES file_entry (id)
    ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS upload_part (
  upload_session_id CHAR(36) NOT NULL,
  part_number INT UNSIGNED NOT NULL,
  etag VARCHAR(255) NOT NULL,
  sha256 BINARY(32) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (upload_session_id, part_number),
  CONSTRAINT fk_upload_part_session
    FOREIGN KEY (upload_session_id) REFERENCES upload_session (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS share_link (
  id CHAR(36) NOT NULL,
  owner_id CHAR(36) NOT NULL,
  root_entry_id CHAR(36) NOT NULL,
  token_hash BINARY(32) NOT NULL,
  access_code_hash VARCHAR(255) NULL,
  access_code_encrypted VARCHAR(1024) NULL,
  permission ENUM('VIEW_ONLY', 'DOWNLOAD') NOT NULL DEFAULT 'DOWNLOAD',
  is_recursive TINYINT(1) NOT NULL DEFAULT 0,
  download_requires_auth TINYINT(1) NOT NULL DEFAULT 1,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_share_link_token_hash (token_hash),
  UNIQUE KEY uk_share_link_owner_root (owner_id, root_entry_id),
  KEY idx_share_link_owner (owner_id, created_at),
  KEY idx_share_link_root (root_entry_id),
  CONSTRAINT fk_share_link_owner
    FOREIGN KEY (owner_id) REFERENCES user_account (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_share_link_root
    FOREIGN KEY (root_entry_id) REFERENCES file_entry (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

SET @share_code_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'share_link'
      AND column_name = 'access_code_encrypted'
);
SET @share_code_column_sql = IF(
    @share_code_column_exists = 0,
    'ALTER TABLE share_link ADD COLUMN access_code_encrypted VARCHAR(1024) NULL AFTER access_code_hash',
    'SELECT 1'
);
PREPARE share_code_column_stmt FROM @share_code_column_sql;
EXECUTE share_code_column_stmt;
DEALLOCATE PREPARE share_code_column_stmt;

DELETE older_share
FROM share_link older_share
JOIN share_link newer_share
  ON newer_share.owner_id = older_share.owner_id
 AND newer_share.root_entry_id = older_share.root_entry_id
 AND (
   (newer_share.revoked_at IS NULL AND older_share.revoked_at IS NOT NULL)
   OR (
     (newer_share.revoked_at IS NULL) = (older_share.revoked_at IS NULL)
     AND (
       newer_share.created_at > older_share.created_at
       OR (
         newer_share.created_at = older_share.created_at
         AND newer_share.id > older_share.id
       )
     )
   )
 );

SET @share_root_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'share_link'
      AND index_name = 'uk_share_link_owner_root'
);
SET @share_root_index_sql = IF(
    @share_root_index_exists = 0,
    'ALTER TABLE share_link ADD UNIQUE KEY uk_share_link_owner_root (owner_id, root_entry_id)',
    'SELECT 1'
);
PREPARE share_root_index_stmt FROM @share_root_index_sql;
EXECUTE share_root_index_stmt;
DEALLOCATE PREPARE share_root_index_stmt;

CREATE TABLE IF NOT EXISTS share_access_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  share_id CHAR(36) NOT NULL,
  viewer_user_id CHAR(36) NULL,
  access_action ENUM('VIEW', 'VERIFY_CODE', 'PREVIEW', 'DOWNLOAD') NOT NULL,
  succeeded TINYINT(1) NOT NULL DEFAULT 1,
  ip_hash BINARY(32) NULL,
  user_agent VARCHAR(512) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_share_access_log_share (share_id, access_action, created_at),
  KEY idx_share_access_log_user (viewer_user_id, created_at),
  CONSTRAINT fk_share_access_log_share
    FOREIGN KEY (share_id) REFERENCES share_link (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_share_access_log_user
    FOREIGN KEY (viewer_user_id) REFERENCES user_account (id)
    ON DELETE SET NULL,
  CONSTRAINT chk_share_download_requires_user
    CHECK (
      access_action <> 'DOWNLOAD'
      OR succeeded = 0
      OR viewer_user_id IS NOT NULL
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS tag (
  id CHAR(36) NOT NULL,
  owner_id CHAR(36) NOT NULL,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(16) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_owner_name (owner_id, name),
  CONSTRAINT fk_tag_owner
    FOREIGN KEY (owner_id) REFERENCES user_account (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS file_tag (
  entry_id CHAR(36) NOT NULL,
  tag_id CHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (entry_id, tag_id),
  KEY idx_file_tag_tag (tag_id),
  CONSTRAINT fk_file_tag_entry
    FOREIGN KEY (entry_id) REFERENCES file_entry (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_file_tag_tag
    FOREIGN KEY (tag_id) REFERENCES tag (id)
    ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS activity_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_id CHAR(36) NOT NULL,
  activity_action ENUM(
    'UPLOAD',
    'CREATE_FOLDER',
    'RENAME',
    'MOVE',
    'COPY',
    'TRASH',
    'RESTORE',
    'DELETE',
    'SHARE_CREATE',
    'SHARE_REVOKE',
    'DOWNLOAD'
  ) NOT NULL,
  entry_id CHAR(36) NULL,
  share_id CHAR(36) NULL,
  event_id CHAR(36) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_event (event_id),
  KEY idx_activity_owner_time (owner_id, created_at),
  KEY idx_activity_entry (entry_id),
  KEY idx_activity_share (share_id),
  CONSTRAINT fk_activity_owner
    FOREIGN KEY (owner_id) REFERENCES user_account (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_activity_share
    FOREIGN KEY (share_id) REFERENCES share_link (id)
    ON DELETE SET NULL
) ENGINE = InnoDB;

-- Effective share state is derived instead of stored:
-- ACTIVE   = revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP(6)
-- EXPIRED  = revoked_at IS NULL AND expires_at <= CURRENT_TIMESTAMP(6)
-- REVOKED  = revoked_at IS NOT NULL
--
-- Share access counts are derived from share_access_log:
-- views     = COUNT(access_action = 'VIEW')
-- downloads = COUNT(access_action = 'DOWNLOAD' AND succeeded = 1)
--
-- Folder share scope is resolved with a recursive CTE from
-- share_link.root_entry_id. New descendants automatically inherit access
-- while share_link.is_recursive = 1.
