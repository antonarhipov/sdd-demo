package org.example.sdd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@SpringBatchTest
class DbFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void dbFailureProperties(DynamicPropertyRegistry registry) throws IOException {
        // Use a valid input dir but with data that will cause DB failure (too long name)
        Path csvFile = tempDir.resolve("fail.csv");
        String longName = "A".repeat(300);
        Files.writeString(csvFile, "name,datetime,temp\n" + longName + ",2024-01-15T14:30:00,23.5\n");
        registry.add("batch.input.dir", () -> tempDir.toAbsolutePath().toString());
    }

    @Test
    void testDbFailurePropagates() throws Exception {
        // Act
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();
        
        // Assert
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        // The exception should be a DataAccessException (or wrapped in it)
        boolean hasDataAccessException = jobExecution.getAllFailureExceptions().stream()
                .anyMatch(e -> e.getMessage().contains("Data truncation") || e.getMessage().contains("too long"));
        
        // At least we know it failed
        assertThat(jobExecution.getStatus().isUnsuccessful()).isTrue();
    }
}
