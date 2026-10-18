package com.hfwas.devops.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 把旧流水线的 repo_url / git_ref / credential_id 回填到 CLONE 任务的参数绑定。
 *
 * <p>SQL 迁移只负责新增参数种子（保持零 UPDATE）；回填在所有 schema 模式下
 * 由应用启动时执行一次，已有 REPO_URL 的任务不会被覆盖。</p>
 */
@Slf4j
@Component
@Order(2)
public class CloneConfigBackfill implements ApplicationRunner {

    private final DataSource dataSource;

    public CloneConfigBackfill(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    void backfill() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='pipeline_job'")) {
                exists = rs.next();
            }
            if (!exists) {
                return;
            }
            int updated = stmt.executeUpdate(
                    "UPDATE pipeline_job " +
                    "SET param_bindings = json_set(" +
                    "  CASE WHEN param_bindings IS NULL OR param_bindings = '' THEN '{}' ELSE param_bindings END, " +
                    "  '$.REPO_URL', json_object('mode', 'fixed', 'value', COALESCE(" +
                    "    (SELECT repo_url FROM pipeline p WHERE p.id = pipeline_job.pipeline_id), '')), " +
                    "  '$.GIT_REF', json_object('mode', 'fixed', 'value', COALESCE(" +
                    "    (SELECT git_ref FROM pipeline p WHERE p.id = pipeline_job.pipeline_id), 'main')), " +
                    "  '$.CREDENTIAL_ID', json_object('mode', 'fixed', 'value', COALESCE(" +
                    "    CAST((SELECT credential_id FROM pipeline p WHERE p.id = pipeline_job.pipeline_id) AS TEXT), ''))" +
                    ") WHERE kind = 'CLONE' AND json_extract(" +
                    "  CASE WHEN param_bindings IS NULL OR param_bindings = '' THEN '{}' ELSE param_bindings END, " +
                    "  '$.REPO_URL') IS NULL");
            if (updated > 0) {
                log.info("backfilled {} clone job(s) to job-level repo config", updated);
            }
        } catch (Exception e) {
            log.warn("backfill clone job config failed: {}", e.getMessage());
        }
    }
}
