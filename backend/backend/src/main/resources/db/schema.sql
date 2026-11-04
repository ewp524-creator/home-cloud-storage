CREATE TABLE IF NOT EXISTS user_account (
    id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    storage_quota_bytes BIGINT UNSIGNED NOT NULL DEFAULT 21474836480,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    deactivated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username),
    UNIQUE KEY uk_user_account_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @user_deactivated_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_account'
      AND column_name = 'deactivated_at'
);
SET @user_deactivated_column_sql = IF(
    @user_deactivated_column_exists = 0,
    'ALTER TABLE user_account ADD COLUMN deactivated_at DATETIME(6) NULL AFTER is_enabled',
    'SELECT 1'
);
PREPARE user_deactivated_column_stmt FROM @user_deactivated_column_sql;
EXECUTE user_deactivated_column_stmt;
DEALLOCATE PREPARE user_deactivated_column_stmt;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS file_object (
    entry_id CHAR(36) NOT NULL,
    storage_bucket VARCHAR(63) NOT NULL,
    storage_object_key VARCHAR(512) NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
    ) NOT NULL DEFAULT 'INITIATED',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE share_access_log
    MODIFY COLUMN access_action
    ENUM('VIEW', 'VERIFY_CODE', 'PREVIEW', 'DOWNLOAD') NOT NULL;

CREATE TABLE IF NOT EXISTS activity_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_id CHAR(36) NOT NULL,
    activity_action VARCHAR(32) NOT NULL,
    entry_id CHAR(36) NULL,
    share_id CHAR(36) NULL,
    event_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_event (event_id),
    KEY idx_activity_owner_time (owner_id, created_at),
    KEY idx_activity_entry (entry_id),
    CONSTRAINT fk_activity_owner
        FOREIGN KEY (owner_id) REFERENCES user_account (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @activity_entry_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'activity_log'
      AND constraint_name = 'fk_activity_entry'
      AND constraint_type = 'FOREIGN KEY'
);
SET @activity_entry_fk_sql = IF(
    @activity_entry_fk_exists > 0,
    'ALTER TABLE activity_log DROP FOREIGN KEY fk_activity_entry',
    'SELECT 1'
);
PREPARE activity_entry_fk_stmt FROM @activity_entry_fk_sql;
EXECUTE activity_entry_fk_stmt;
DEALLOCATE PREPARE activity_entry_fk_stmt;

SET @activity_event_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'activity_log'
      AND column_name = 'event_id'
);
SET @activity_event_column_sql = IF(
    @activity_event_column_exists = 0,
    'ALTER TABLE activity_log ADD COLUMN event_id CHAR(36) NULL AFTER share_id',
    'SELECT 1'
);
PREPARE activity_event_column_stmt FROM @activity_event_column_sql;
EXECUTE activity_event_column_stmt;
DEALLOCATE PREPARE activity_event_column_stmt;

SET @activity_event_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'activity_log'
      AND index_name = 'uk_activity_event'
);
SET @activity_event_index_sql = IF(
    @activity_event_index_exists = 0,
    'ALTER TABLE activity_log ADD UNIQUE KEY uk_activity_event (event_id)',
    'SELECT 1'
);
PREPARE activity_event_index_stmt FROM @activity_event_index_sql;
EXECUTE activity_event_index_stmt;
DEALLOCATE PREPARE activity_event_index_stmt;
