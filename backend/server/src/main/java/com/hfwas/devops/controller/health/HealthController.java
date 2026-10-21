package com.hfwas.devops.controller.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Liveness and readiness endpoints.
 *
 * @author houfei
 * @package com.hfwas.devops.controller.health
 * @date 2025/6/12
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/check")
    public String health() {
        return "UP";
    }

    /**
     * Readiness checks the MySQL connection used by Flyway/MyBatis.
     */
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next()
                    ? ResponseEntity.ok("UP")
                    : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("DOWN");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("DOWN");
        }
    }
}
