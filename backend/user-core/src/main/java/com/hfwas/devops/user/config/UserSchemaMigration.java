package com.hfwas.devops.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class UserSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        ensureUserTable();
        ensureSessionTable();
        ensureLoginLogTable();
        seedAdminUser();
    }

    private void ensureUserTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    username        TEXT         NOT NULL UNIQUE,
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

    private void seedAdminUser() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE del_flag = 0", Long.class);
        if (count != null && count > 0) {
            return;
        }
        String encoded = passwordEncoder.encode("admin123");
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password, display_name, role, enabled)
                VALUES (?, ?, ?, 'admin', 1)
                """, "admin", encoded, "系统管理员");
        log.info("Seeded default admin user: admin / admin123");
    }
}
