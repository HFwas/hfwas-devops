package com.hfwas.devops.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class PmTenantMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("pm_project")) {
            return;
        }
        addColumnIfMissing("pm_project", "tenant_id", "INTEGER DEFAULT 1");
        jdbcTemplate.update("UPDATE pm_project SET tenant_id = 1 WHERE tenant_id IS NULL");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_pm_project_tenant ON pm_project(tenant_id)");
        migrateProjectCodeUniqueByTenant();
    }

    /** Replace global unique(code) with unique(tenant_id, code) for multi-tenant projects. */
    private void migrateProjectCodeUniqueByTenant() {
        if (indexExists("idx_pm_project_tenant_code")) {
            return;
        }
        if (!tableExists("pm_project")) {
            return;
        }
        log.info("Migrating pm_project: tenant-scoped project code uniqueness");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pm_project__tenant_mig (
                    id              INTEGER      NOT NULL PRIMARY KEY,
                    tenant_id       INTEGER      NOT NULL DEFAULT 1,
                    code            TEXT         NOT NULL,
                    name            TEXT         NOT NULL,
                    description     TEXT,
                    settings        TEXT,
                    create_by       INTEGER,
                    update_by       INTEGER,
                    create_time     TEXT,
                    update_time     TEXT,
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO pm_project__tenant_mig
                (id, tenant_id, code, name, description, settings, create_by, update_by, create_time, update_time, del_flag)
                SELECT id, tenant_id, code, name, description, settings, create_by, update_by, create_time, update_time, del_flag
                FROM pm_project
                """);
        jdbcTemplate.execute("DROP TABLE pm_project");
        jdbcTemplate.execute("ALTER TABLE pm_project__tenant_mig RENAME TO pm_project");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_pm_project_tenant_code ON pm_project(tenant_id, code)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_pm_project_tenant ON pm_project(tenant_id)");
    }

    private boolean indexExists(String indexName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name = ?", Long.class, indexName);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?", Long.class, table);
        return count != null && count > 0;
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
