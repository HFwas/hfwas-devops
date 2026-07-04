package com.hfwas.devops.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class UserSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        ensureTenantTable();
        ensureUserTable();
        migrateTenantColumns();
        ensureUserTenantIndex();
        ensureTenantMemberTable();
        migrateToTenantMembers();
        ensureGlobalUsernameIndex();
        ensureSessionTable();
        ensureLoginLogTable();
        ensureOperLogTable();
        ensureIdentityConnectorTable();
        migrateUserAuthSourceColumns();
        ensureUserMessageTable();
        ensureNotifyChannelTable();
        seedDefaultTenant();
        seedAdminUser();
    }

    private void ensureTenantTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_tenant (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    code            TEXT         NOT NULL UNIQUE,
                    name            TEXT         NOT NULL,
                    contact_name    TEXT,
                    contact_phone   TEXT,
                    status          INTEGER      DEFAULT 1,
                    remark          TEXT,
                    create_time     TEXT         DEFAULT (datetime('now')),
                    update_time     TEXT         DEFAULT (datetime('now')),
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_tenant_code ON sys_tenant(code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant(status)");
    }

    private void ensureUserTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    tenant_id       INTEGER      NOT NULL DEFAULT 1,
                    username        TEXT         NOT NULL,
                    password        TEXT         NOT NULL,
                    display_name    TEXT         NOT NULL,
                    email           TEXT,
                    phone           TEXT,
                    role            TEXT         NOT NULL DEFAULT 'user',
                    enabled         INTEGER      DEFAULT 1,
                    create_time     TEXT         DEFAULT (datetime('now')),
                    update_time     TEXT         DEFAULT (datetime('now')),
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username)");
    }

    private void ensureUserTenantIndex() {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_tenant_username ON sys_user(tenant_id, username)");
    }

    private void migrateTenantColumns() {
        addColumnIfMissing("sys_user", "tenant_id", "INTEGER DEFAULT 1");
        jdbcTemplate.update("UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL");
    }

    private void ensureTenantMemberTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_tenant_member (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    tenant_id       INTEGER      NOT NULL,
                    user_id         INTEGER      NOT NULL,
                    tenant_role     TEXT         NOT NULL DEFAULT 'member',
                    status          INTEGER      DEFAULT 1,
                    join_time       TEXT         DEFAULT (datetime('now')),
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tenant_member ON sys_tenant_member(tenant_id, user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_user ON sys_tenant_member(user_id)");
    }

    private void migrateToTenantMembers() {
        Long memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_member WHERE del_flag = 0", Long.class);
        if (memberCount != null && memberCount > 0) {
            return;
        }
        if (!columnExists("sys_user", "tenant_id")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO sys_tenant_member (tenant_id, user_id, tenant_role, status)
                SELECT tenant_id, id,
                       CASE WHEN role = 'admin' THEN 'tenant_admin' ELSE 'member' END,
                       COALESCE(enabled, 1)
                FROM sys_user
                WHERE tenant_id IS NOT NULL AND del_flag = 0
                """);
        log.info("Migrated sys_user.tenant_id into sys_tenant_member");
    }

    private void ensureGlobalUsernameIndex() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_sys_user_tenant_username");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_username_unique ON sys_user(username)");
    }

    private void ensureSessionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_user_session (
                    id               INTEGER      PRIMARY KEY AUTOINCREMENT,
                    user_id          INTEGER      NOT NULL,
                    jti              TEXT         NOT NULL UNIQUE,
                    login_ip         TEXT,
                    user_agent       TEXT,
                    login_time       TEXT         DEFAULT (datetime('now')),
                    last_active_time TEXT         DEFAULT (datetime('now')),
                    expire_time      TEXT         NOT NULL,
                    revoked          INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_session_user ON sys_user_session(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_session_jti ON sys_user_session(jti)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_session_active ON sys_user_session(revoked, expire_time)");
    }

    private void ensureLoginLogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_login_log (
                    id            INTEGER      PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER,
                    username      TEXT         NOT NULL,
                    display_name  TEXT,
                    action        TEXT         NOT NULL,
                    login_ip      TEXT,
                    user_agent    TEXT,
                    client_info   TEXT,
                    fail_reason   TEXT,
                    create_time   TEXT         DEFAULT (datetime('now'))
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_login_log_time ON sys_login_log(create_time)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_login_log_user ON sys_login_log(username)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_login_log_action ON sys_login_log(action)");
    }

    private void ensureOperLogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_oper_log (
                    id            INTEGER      PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER,
                    username      TEXT,
                    display_name  TEXT,
                    module        TEXT         NOT NULL,
                    action        TEXT         NOT NULL,
                    biz_type      TEXT,
                    biz_id        TEXT,
                    summary       TEXT         NOT NULL,
                    status        TEXT         DEFAULT 'success',
                    fail_reason   TEXT,
                    request_ip    TEXT,
                    user_agent    TEXT,
                    client_info   TEXT,
                    extra_json    TEXT,
                    create_time   TEXT         DEFAULT (datetime('now'))
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_oper_log_time ON sys_oper_log(create_time)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_oper_log_module ON sys_oper_log(module)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_oper_log_user ON sys_oper_log(username)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_oper_log_action ON sys_oper_log(action)");
    }

    private void ensureIdentityConnectorTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_identity_connector (
                    id                  INTEGER      PRIMARY KEY AUTOINCREMENT,
                    name                TEXT         NOT NULL,
                    type                TEXT         NOT NULL,
                    config_json         TEXT         NOT NULL,
                    enabled             INTEGER      DEFAULT 1,
                    default_tenant_id   INTEGER,
                    auto_create_member  INTEGER      DEFAULT 1,
                    last_sync_time      TEXT,
                    last_sync_status    TEXT,
                    last_sync_message   TEXT,
                    last_sync_count     INTEGER,
                    create_time         TEXT         DEFAULT (datetime('now')),
                    update_time         TEXT         DEFAULT (datetime('now')),
                    del_flag            INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_identity_connector_type ON sys_identity_connector(type)");
    }

    private void migrateUserAuthSourceColumns() {
        addColumnIfMissing("sys_user", "auth_source", "TEXT DEFAULT 'local'");
        addColumnIfMissing("sys_user", "external_id", "TEXT");
        addColumnIfMissing("sys_user", "connector_id", "INTEGER");
        jdbcTemplate.update("UPDATE sys_user SET auth_source = 'local' WHERE auth_source IS NULL");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_connector ON sys_user(connector_id, external_id)");
    }

    private void ensureUserMessageTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_user_message (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    user_id         INTEGER      NOT NULL,
                    tenant_id       INTEGER,
                    category        TEXT         NOT NULL DEFAULT 'operation',
                    title           TEXT         NOT NULL,
                    content         TEXT,
                    read_flag       INTEGER      DEFAULT 0,
                    sender_id       INTEGER,
                    sender_name     TEXT,
                    biz_type        TEXT,
                    biz_id          TEXT,
                    link_url        TEXT,
                    create_time     TEXT         DEFAULT (datetime('now')),
                    read_time       TEXT,
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_message_user ON sys_user_message(user_id, read_flag)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_user_message_time ON sys_user_message(create_time)");
    }

    private void ensureNotifyChannelTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_notify_channel (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    channel         TEXT         NOT NULL UNIQUE,
                    enabled         INTEGER      DEFAULT 0,
                    config_json     TEXT,
                    remark          TEXT,
                    create_time     TEXT         DEFAULT (datetime('now')),
                    update_time     TEXT         DEFAULT (datetime('now')),
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
    }

    private void seedDefaultTenant() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_tenant WHERE del_flag = 0", Long.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_tenant (id, code, name, status, remark)
                VALUES (1, 'default', '默认租户', 1, '系统内置默认租户')
                """);
        log.info("Seeded default tenant: default / 默认租户");
    }

    private void seedAdminUser() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE del_flag = 0", Long.class);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO sys_tenant_member (tenant_id, user_id, tenant_role, status)
                    SELECT COALESCE(tenant_id, 1), id,
                           CASE WHEN role = 'admin' THEN 'tenant_admin' ELSE 'member' END,
                           COALESCE(enabled, 1)
                    FROM sys_user WHERE del_flag = 0
                    """);
            return;
        }
        String encoded = passwordEncoder.encode("admin123");
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password, display_name, role, enabled)
                VALUES (?, ?, ?, 'admin', 1)
                """, "admin", encoded, "系统管理员");
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = 'admin'", Long.class);
        if (adminId != null) {
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO sys_tenant_member (tenant_id, user_id, tenant_role, status)
                    VALUES (1, ?, 'tenant_admin', 1)
                    """, adminId);
        }
        log.info("Seeded default admin user: admin / admin123 (member of tenant=default)");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("Added column {}.{}", table, column);
    }

    private boolean columnExists(String table, String column) {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> col : cols) {
            if (column.equalsIgnoreCase(String.valueOf(col.get("name")))) {
                return true;
            }
        }
        return false;
    }
}
