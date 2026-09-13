package com.hfwas.devops.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "devops.schema.init", havingValue = "embedded", matchIfMissing = true)
public class SqliteSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    public SqliteSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(Path.of("data"));
        dropStaleDependencyComponentTable();
        migrateTaskKindTable();
        migratePipelineRunTable();
        migratePipelineJobParamTable();
        migratePipelineJobBindings();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/pm-schema.sql"));
        populator.addScript(new ClassPathResource("db/api-test-schema.sql"));
        populator.addScript(new ClassPathResource("db/image-schema.sql"));
        populator.addScript(new ClassPathResource("db/pipeline-schema.sql"));
        populator.addScript(new ClassPathResource("db/container-schema.sql"));
        populator.setSeparator(";");
        populator.setContinueOnError(true);
        populator.execute(dataSource);
        log.info("SQLite schema initialized at ./data/hfwas-devops.db");
    }

    /** 旧表无 pipeline_id / identity_key，直接丢掉重建（组件可从 SBOM 再解析）。 */
    private void dropStaleDependencyComponentTable() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='dependency_component'")) {
                exists = rs.next();
            }
            if (!exists) {
                return;
            }
            boolean hasPipelineId = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(dependency_component)")) {
                while (rs.next()) {
                    if ("pipeline_id".equalsIgnoreCase(rs.getString("name"))) {
                        hasPipelineId = true;
                        break;
                    }
                }
            }
            if (!hasPipelineId) {
                stmt.execute("DROP TABLE dependency_component");
                log.info("dropped stale dependency_component (missing pipeline_id)");
            }
        } catch (Exception e) {
            log.warn("check dependency_component schema failed: {}", e.getMessage());
        }
    }

    /** 为 pipeline_task_kind 表追加 4 个资源配额列（v0.2 新增）。 */
    private void migrateTaskKindTable() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pipeline_task_kind'")) {
                exists = rs.next();
            }
            if (!exists) return;
            boolean hasCpuRequest = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(pipeline_task_kind)")) {
                while (rs.next()) {
                    if ("cpu_request".equalsIgnoreCase(rs.getString("name"))) {
                        hasCpuRequest = true;
                        break;
                    }
                }
            }
            if (!hasCpuRequest) {
                stmt.execute("ALTER TABLE pipeline_task_kind ADD COLUMN cpu_request TEXT NOT NULL DEFAULT ''");
                stmt.execute("ALTER TABLE pipeline_task_kind ADD COLUMN cpu_limit TEXT NOT NULL DEFAULT ''");
                stmt.execute("ALTER TABLE pipeline_task_kind ADD COLUMN memory_request TEXT NOT NULL DEFAULT ''");
                stmt.execute("ALTER TABLE pipeline_task_kind ADD COLUMN memory_limit TEXT NOT NULL DEFAULT ''");
                log.info("migrated pipeline_task_kind: added cpu/memory columns");
            }
        } catch (Exception e) {
            log.warn("migrate pipeline_task_kind failed: {}", e.getMessage());
        }
    }

    /** 为 pipeline_run 表追加 runtime_params 列。 */
    private void migratePipelineRunTable() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pipeline_run'")) {
                exists = rs.next();
            }
            if (!exists) return;
            boolean hasRuntimeParams = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(pipeline_run)")) {
                while (rs.next()) {
                    if ("runtime_params".equalsIgnoreCase(rs.getString("name"))) {
                        hasRuntimeParams = true;
                        break;
                    }
                }
            }
            if (!hasRuntimeParams) {
                stmt.execute("ALTER TABLE pipeline_run ADD COLUMN runtime_params TEXT NOT NULL DEFAULT ''");
                log.info("migrated pipeline_run: added runtime_params column");
            }
        } catch (Exception e) {
            log.warn("migrate pipeline_run failed: {}", e.getMessage());
        }
    }

    /** 为 pipeline_job_param 表追加 value_mode 列。 */
    private void migratePipelineJobParamTable() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pipeline_job_param'")) {
                exists = rs.next();
            }
            if (!exists) return;
            boolean hasValueMode = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(pipeline_job_param)")) {
                while (rs.next()) {
                    if ("value_mode".equalsIgnoreCase(rs.getString("name"))) {
                        hasValueMode = true;
                        break;
                    }
                }
            }
            if (!hasValueMode) {
                stmt.execute("ALTER TABLE pipeline_job_param ADD COLUMN value_mode TEXT NOT NULL DEFAULT 'runtime'");
                log.info("migrated pipeline_job_param: added value_mode column");
            }
        } catch (Exception e) {
            log.warn("migrate pipeline_job_param failed: {}", e.getMessage());
        }
    }

    /** 为 pipeline_job 追加 param_bindings 列。 */
    private void migratePipelineJobBindings() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pipeline_job'")) {
                exists = rs.next();
            }
            if (!exists) return;
            boolean hasBindings = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(pipeline_job)")) {
                while (rs.next()) {
                    if ("param_bindings".equalsIgnoreCase(rs.getString("name"))) {
                        hasBindings = true;
                        break;
                    }
                }
            }
            if (!hasBindings) {
                stmt.execute("ALTER TABLE pipeline_job ADD COLUMN param_bindings TEXT NOT NULL DEFAULT '{}'");
                log.info("migrated pipeline_job: added param_bindings column");
            }
        } catch (Exception e) {
            log.warn("migrate pipeline_job bindings failed: {}", e.getMessage());
        }
    }
}
