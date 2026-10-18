package com.hfwas.devops.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class CloneConfigBackfillTest {

    @Test
    void backfillsLegacyCloneBindingsAndIsIdempotent() throws Exception {
        Path db = Files.createTempFile("clone-config", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + db);
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE pipeline (id INTEGER PRIMARY KEY, repo_url TEXT, git_ref TEXT, credential_id INTEGER)");
            stmt.execute("CREATE TABLE pipeline_job (id INTEGER PRIMARY KEY, pipeline_id INTEGER, kind TEXT, " +
                    "param_bindings TEXT NOT NULL DEFAULT '{}')");
            stmt.execute("INSERT INTO pipeline VALUES (1, 'https://github.com/acme/app.git', 'dev', 7)");
            stmt.execute("INSERT INTO pipeline_job VALUES (10, 1, 'CLONE', " +
                    "'{\"GIT_REF\":{\"mode\":\"fixed\",\"value\":\"main\"}}')");
        }

        CloneConfigBackfill backfill = new CloneConfigBackfill(dataSource);
        backfill.backfill();
        String first = readBindings(dataSource);
        assertThat(first)
                .contains("\"REPO_URL\":{\"mode\":\"fixed\",\"value\":\"https://github.com/acme/app.git\"}")
                .contains("\"GIT_REF\":{\"mode\":\"fixed\",\"value\":\"dev\"}")
                .contains("\"CREDENTIAL_ID\":{\"mode\":\"fixed\",\"value\":\"7\"}");

        backfill.backfill();
        assertThat(readBindings(dataSource)).isEqualTo(first);

        Files.deleteIfExists(db);
    }

    @Test
    void doesNotOverwriteAnExistingRepoUrl() throws Exception {
        Path db = Files.createTempFile("clone-config-existing", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + db);
        String existing = "{\"REPO_URL\":{\"mode\":\"fixed\",\"value\":\"https://github.com/acme/other.git\"}}";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE pipeline (id INTEGER PRIMARY KEY, repo_url TEXT, git_ref TEXT, credential_id INTEGER)");
            stmt.execute("CREATE TABLE pipeline_job (id INTEGER PRIMARY KEY, pipeline_id INTEGER, kind TEXT, " +
                    "param_bindings TEXT NOT NULL DEFAULT '{}')");
            stmt.execute("INSERT INTO pipeline VALUES (1, 'https://github.com/acme/app.git', 'dev', 7)");
            stmt.execute("INSERT INTO pipeline_job VALUES (10, 1, 'CLONE', '" + existing + "')");
        }

        new CloneConfigBackfill(dataSource).backfill();
        assertThat(readBindings(dataSource)).isEqualTo(existing);

        Files.deleteIfExists(db);
    }

    private static String readBindings(DriverManagerDataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT param_bindings FROM pipeline_job WHERE id = 10")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }
}
