package org.example.sdd.tempimport;

import org.example.sdd.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.batch.job.enabled=false",
    "app.import.input-dir=target/test-empty"
})
@DirtiesContext
public class EmptyDirectoryIntegrationTest {

    private static final Path inputDir = Paths.get("target/test-empty");

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
    }

    @AfterAll
    public static void afterAll() throws IOException {
        cleanupDir(inputDir);
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
    public void testJobEmptyInputDirectory() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobOperator.start(temperatureImportJob, jobParameters);
        assertEquals("COMPLETED", jobExecution.getStatus().toString());

        // Verify that the database is empty
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_reading", Integer.class);
        assertEquals(0, count);
    }
}
