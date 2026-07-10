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
public class PmModuleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureModuleTable();
        ensureModuleIdColumn();
        ensureStatusLayoutColumns();
        ensureWorkItemTypeColor();
        ensureProjectIssueTypeTable();
        seedWorkItemTypeColors();
    }

    private void ensureModuleTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pm_project_module (
                    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
                    project_id      INTEGER      NOT NULL,
                    parent_id       INTEGER,
                    name            TEXT         NOT NULL,
                    description     TEXT,
                    sort_order      INTEGER      DEFAULT 0,
                    enabled         INTEGER      DEFAULT 1,
                    create_time     TEXT         DEFAULT (datetime('now')),
                    update_time     TEXT         DEFAULT (datetime('now')),
                    del_flag        INTEGER      DEFAULT 0
                )
                """);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pm_project_module ON pm_project_module(project_id, parent_id)"
        );
    }

    private void ensureModuleIdColumn() {
        if (!hasColumn("pm_work_item", "module_id")) {
            jdbcTemplate.execute("ALTER TABLE pm_work_item ADD COLUMN module_id INTEGER");
            log.info("Added pm_work_item.module_id column");
        }
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pm_work_item_module ON pm_work_item(module_id)"
        );
    }

    private void ensureStatusLayoutColumns() {
        if (!hasColumn("pm_status_definition", "layout_x")) {
            jdbcTemplate.execute("ALTER TABLE pm_status_definition ADD COLUMN layout_x REAL");
            log.info("Added pm_status_definition.layout_x column");
        }
        if (!hasColumn("pm_status_definition", "layout_y")) {
            jdbcTemplate.execute("ALTER TABLE pm_status_definition ADD COLUMN layout_y REAL");
            log.info("Added pm_status_definition.layout_y column");
        }
    }

    private void ensureWorkItemTypeColor() {
        if (!hasColumn("pm_work_item_type", "color")) {
            jdbcTemplate.execute("ALTER TABLE pm_work_item_type ADD COLUMN color TEXT");
            log.info("Added pm_work_item_type.color column");
        }
    }

    private void ensureProjectIssueTypeTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pm_project_issue_type (
                    id          INTEGER      PRIMARY KEY AUTOINCREMENT,
                    project_id  INTEGER      NOT NULL,
                    type_code   TEXT         NOT NULL,
                    sort_order  INTEGER      DEFAULT 0,
                    UNIQUE(project_id, type_code)
                )
                """);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pm_project_issue_type ON pm_project_issue_type(project_id)"
        );
    }

    private void seedWorkItemTypeColors() {
        updateTypeColorIfBlank("requirement", "#2080f0");
        updateTypeColorIfBlank("task", "#18a058");
        updateTypeColorIfBlank("bug", "#d03050");
        updateTypeColorIfBlank("test_case", "#f0a020");
    }

    private void updateTypeColorIfBlank(String code, String color) {
        jdbcTemplate.update(
                "UPDATE pm_work_item_type SET color = ? WHERE code = ? AND (color IS NULL OR color = '')",
                color, code);
    }

    private boolean hasColumn(String table, String column) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
    }
}
