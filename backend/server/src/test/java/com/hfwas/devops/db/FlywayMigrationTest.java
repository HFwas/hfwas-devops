package com.hfwas.devops.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 迁移基线测试：空库执行 Flyway，校验表、种子与索引。
 * 无 Docker 环境自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("hfwas_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00");

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .placeholderReplacement(false)
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        flyway.validate();
    }

    @Test
    void schemaAndSeedAreComplete() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertEquals(58, count(s, "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
                    "AND table_name <> 'flyway_schema_history'"));
            assertEquals(3, count(s, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM sys_tenant"));
            assertEquals(5, count(s, "SELECT COUNT(*) FROM pm_work_item_type"));
            assertEquals(20, count(s, "SELECT COUNT(*) FROM pm_status_definition"));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM pm_work_item_type WHERE code = 'iteration'"));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM pm_work_item_type " +
                    "WHERE code = 'iteration' AND built_in = 1 AND sort_order = 4"));
            assertEquals(4, count(s, "SELECT COUNT(*) FROM pm_status_definition WHERE type_code = 'iteration'"));
            assertEquals(19, count(s, "SELECT COUNT(*) FROM pipeline_task_kind"));
            assertEquals(23, count(s, "SELECT COUNT(*) FROM pipeline_task_kind_param"));
            assertEquals(19, count(s, "SELECT COUNT(DISTINCT id) FROM pipeline_task_kind"));
        }
    }

    @Test
    void triggerColumnIsRenamed() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertEquals(1, count(s, "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'pipeline_run' " +
                    "AND column_name = 'trigger_type'"));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'pipeline_run' " +
                    "AND column_name = 'trigger'"));
        }
    }

    @Test
    void noTextColumnsAreIndexed() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                     SELECT s.table_name, s.index_name, s.column_name, c.data_type
                     FROM information_schema.statistics s
                     JOIN information_schema.columns c
                       ON c.table_schema = s.table_schema
                      AND c.table_name = s.table_name
                      AND c.column_name = s.column_name
                     WHERE s.table_schema = DATABASE()
                       AND c.data_type IN ('text','mediumtext','longtext','blob')
                     """)) {
            assertTrue(!rs.next(), "indexed TEXT/BLOB column found");
        }
    }

    @Test
    void pmFoundationColumnsExist() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertEquals(1, columnCount(s, "pm_work_item", "end_time", "datetime"));
            assertEquals(1, columnCount(s, "pm_work_item", "version", "bigint"));
            assertEquals(0, count(s, "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'pm_work_item' " +
                    "AND column_name = 'version' AND is_nullable = 'YES'"));
            assertEquals(1, columnCount(s, "pm_work_item_type", "built_in", "tinyint"));
            assertEquals(1, columnCount(s, "pm_work_item_type", "description", "varchar"));
            assertEquals(1, columnCount(s, "pm_work_item_type", "template_code", "varchar"));
            assertEquals(1, columnCount(s, "pm_field_definition", "batch_editable", "tinyint"));
            assertEquals(1, columnCount(s, "pm_field_definition", "batch_order", "int"));
            assertEquals(1, columnCount(s, "pm_work_item_activity", "batch_id", "varchar"));
            assertEquals(1, columnCount(s, "pm_work_item_activity", "old_value", "mediumtext"));
            assertEquals(1, columnCount(s, "pm_work_item_activity", "new_value", "mediumtext"));
        }
    }

    @Test
    void pmFoundationSchemaObjectsExist() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertEquals(1, count(s, "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = 'pm_work_item_watcher'"));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = 'pm_batch_action_config'"));
            assertEquals(1, indexCount(s, "pm_work_item", "idx_pm_work_item_end_time"));
            assertEquals(1, indexCount(s, "pm_work_item_activity", "idx_pm_work_item_activity_batch"));
        }
    }

    private static long columnCount(Statement s, String table, String column, String dataType) throws Exception {
        return count(s, "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = '" + table + "' " +
                "AND column_name = '" + column + "' AND data_type = '" + dataType + "'");
    }

    private static long indexCount(Statement s, String table, String index) throws Exception {
        // A composite index has one row per indexed column; count the index itself.
        return count(s, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = '" + table + "' " +
                "AND index_name = '" + index + "'");
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long count(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
