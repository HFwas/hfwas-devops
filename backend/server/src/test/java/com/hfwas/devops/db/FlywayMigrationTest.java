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
            assertEquals(56, count(s, "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
                    "AND table_name <> 'flyway_schema_history'"));
            assertEquals(2, count(s, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1, count(s, "SELECT COUNT(*) FROM sys_tenant"));
            assertEquals(4, count(s, "SELECT COUNT(*) FROM pm_work_item_type"));
            assertEquals(16, count(s, "SELECT COUNT(*) FROM pm_status_definition"));
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
