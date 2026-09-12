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
}
