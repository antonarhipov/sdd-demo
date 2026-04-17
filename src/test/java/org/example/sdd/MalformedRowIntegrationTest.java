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
class MalformedRowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Path tempDir;

    static {
        try {
            tempDir = Files.createTempDirectory("batch-test-malformed");
            Path csvFile = tempDir.resolve("malformed.csv");
            Files.writeString(csvFile, """
                    name,datetime,temp
                    Valid1,2024-01-15T14:30:00,23.5
                    BadDate,invalid-date,24.0
                    BadTemp,2024-01-15T14:45:00,not-a-number
                    MissingField,2024-01-15T15:00:00,
                    Valid2,2024-01-15T15:15:00,18.2
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
    void testMalformedRows() throws Exception {
        // Act: Run the job
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        // Only 2 valid records should be in the DB
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM temperature_records", Integer.class);
        assertThat(count).isEqualTo(2);

        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        int inserted = stepExecution.getExecutionContext().getInt("insertedCount", 0);
        int malformed = stepExecution.getExecutionContext().getInt("malformedCount", 0);

        assertThat(inserted).isEqualTo(2);
        assertThat(malformed).isEqualTo(3); // BadDate, BadTemp, MissingField
    }
}
