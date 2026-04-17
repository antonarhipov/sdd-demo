package org.example.sdd;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
class DuplicateDetectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Path tempDir;

    static {
        try {
            tempDir = Files.createTempDirectory("batch-test-duplicate");
            Path csvFile = tempDir.resolve("data.csv");
            // CSV contains 2 existing records (from pre-seed) and 2 new records
            Files.writeString(csvFile, """
                    name,datetime,temp
                    Station1,2024-01-15T14:30:00,23.5
                    Station1,2024-01-15T14:45:00,24.0
                    Station2,2024-01-15T14:30:00,18.2
                    Station2,2024-01-15T14:45:00,19.0
                    """);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void inputDirProperty(DynamicPropertyRegistry registry) {
        registry.add("batch.input.dir", () -> tempDir.toAbsolutePath().toString());
    }

    @Test
    void testDuplicateDetection() throws Exception {
        // Arrange: Pre-seed the database
        jdbcTemplate.update("INSERT INTO temperature_records (name, datetime, temp) VALUES (?, ?, ?)",
                "Station1", "2024-01-15 14:30:00", 23.5);
        jdbcTemplate.update("INSERT INTO temperature_records (name, datetime, temp) VALUES (?, ?, ?)",
                "Station1", "2024-01-15 14:45:00", 24.0);

        // Act: Run the job
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        // Total in DB should be 4 (2 pre-seeded + 2 new)
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM temperature_records", Integer.class);
        assertThat(count).isEqualTo(4);

        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        int inserted = stepExecution.getExecutionContext().getInt("insertedCount");
        int duplicates = stepExecution.getExecutionContext().getInt("duplicateCount");

        assertThat(inserted).isEqualTo(2);
        assertThat(duplicates).isEqualTo(2);
    }
}
