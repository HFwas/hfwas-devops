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
        ensureSessionTable();
        ensureLoginLogTable();
        ensureOperLogTable();
        migrateTenantColumns();
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
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_tenant_username ON sys_user(tenant_id, username)");
    }

    private void migrateTenantColumns() {
        addColumnIfMissing("sys_user", "tenant_id", "INTEGER DEFAULT 1");
        jdbcTemplate.update("UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL");
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
            jdbcTemplate.update("UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL");
            return;
        }
        String encoded = passwordEncoder.encode("admin123");
        jdbcTemplate.update("""
                INSERT INTO sys_user (tenant_id, username, password, display_name, role, enabled)
                VALUES (1, ?, ?, ?, 'admin', 1)
                """, "admin", encoded, "系统管理员");
        log.info("Seeded default admin user: admin / admin123 (tenant=default)");
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
