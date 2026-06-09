package org.example.sdd.tempimport;

import org.example.sdd.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.batch.job.enabled=false",
    "app.import.input-dir=target/test-missing-header"
})
@DirtiesContext
public class MissingHeaderIntegrationTest {

    private static final Path inputDir = Paths.get("target/test-missing-header");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job temperatureImportJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void beforeAll() throws IOException {
        cleanupDir(inputDir);
        Files.createDirectories(inputDir);
        Path badCsv = inputDir.resolve("bad_headers.csv");
        Files.write(badCsv, List.of(
            "name,datetime",
            "Alice,2026-06-09T10:00:00"
        ));
    }

    @AfterAll
    public static void afterAll() throws IOException {
        cleanupDir(inputDir);
        cleanupDir(Paths.get("target/processed"));
        cleanupDir(Paths.get("target/failed"));
    }

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM temperature_reading");
    }

    private static void cleanupDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException ignored) {}
                });
            }
            Files.delete(dir);
        }
    }

    @Test
    public void testJobMissingHeaderFailureMovesToFailed() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobOperator.start(temperatureImportJob, jobParameters);
        assertEquals("COMPLETED", jobExecution.getStatus().toString());

        // No rows should be inserted
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_reading", Integer.class);
        assertEquals(0, count);

        // File should be moved to failed/
        assertFalse(Files.exists(inputDir.resolve("bad_headers.csv")));
        assertTrue(Files.exists(Paths.get("target/failed/bad_headers.csv")));
    }
}
