package org.example.sdd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
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
class HappyPathIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Path tempDir;
    
    static {
        try {
            tempDir = Files.createTempDirectory("batch-test-happy");
            Path csvFile = tempDir.resolve("test.csv");
            Files.writeString(csvFile, """
                    name,datetime,temp
                    Station1,2024-01-15T14:30:00,23.5
                    Station1,2024-01-15T14:45:00,24.0
                    Station2,2024-01-15T14:30:00,18.2
                    """);
            System.out.println("[DEBUG_LOG] Created test file at: " + csvFile.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void inputDirProperty(DynamicPropertyRegistry registry) {
        registry.add("batch.input.dir", () -> tempDir.toAbsolutePath().toString());
    }

    @Test
    void testHappyPath() throws Exception {
        // Act: Run the job
        JobExecution jobExecution = jobOperatorTestUtils.startJob();

        // Assert
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM temperature_records", Integer.class);
        assertThat(count).isEqualTo(3);
        
        // Final summary should be logged (we can't easily assert logs here, but counts in StepExecution should match)
        // jobLauncherTestUtils doesn't give us the summary directly, but we can check StepExecution context
        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        int inserted = stepExecution.getExecutionContext().getInt("insertedCount");
        assertThat(inserted).isEqualTo(3);
    }
}
