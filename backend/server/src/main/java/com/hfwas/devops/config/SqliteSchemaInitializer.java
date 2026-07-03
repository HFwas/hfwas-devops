package com.hfwas.devops.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@Order(1)
public class SqliteSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    public SqliteSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(Path.of("data"));
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/pm-schema.sql"));
        populator.setSeparator(";");
        populator.setContinueOnError(true);
        populator.execute(dataSource);
        log.info("SQLite PM schema initialized at ./data/hfwas-devops.db");
    }
}
