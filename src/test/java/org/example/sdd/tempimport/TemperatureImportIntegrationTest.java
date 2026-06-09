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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.batch.job.enabled=false",
    "app.import.input-dir=target/test-input"
})
public class TemperatureImportIntegrationTest {

    private static final Path inputDir = Paths.get("target/test-input");

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job temperatureImportJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void beforeAll() throws IOException {
        Files.createDirectories(inputDir);
        Path csvFile = inputDir.resolve("temperatures.csv");
        Files.write(csvFile, List.of(
            "name,datetime,temp",
            "Alice,2026-06-09T10:00:00,23.5",
            "Bob,2026-06-09T10:15:00,22.8",
            "  ,2026-06-09T10:20:00,21.0"
        ));
    }

    @AfterAll
    public static void afterAll() throws IOException {
        if (Files.exists(inputDir)) {
            try (var stream = Files.list(inputDir)) {
                stream.forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException ignored) {}
                });
            }
            Files.delete(inputDir);
        }
    }

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM temperature_reading");
    }

    @Test
    public void testJobE2EWithValidCsv() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncher.run(temperatureImportJob, jobParameters);
        assertEquals("COMPLETED", jobExecution.getStatus().toString());

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_reading", Integer.class);
        assertNotNull(count);
        assertEquals(2, count);
    }
}
