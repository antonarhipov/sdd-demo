package org.example.sdd;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EdgeCaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Path tempDir;

    static {
        try {
            tempDir = Files.createTempDirectory("batch-test-edge");
            Files.writeString(tempDir.resolve("header_only.csv"), "name,datetime,temp\n");
            Files.createDirectories(tempDir.resolve("empty_dir"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void inputDirProperty(DynamicPropertyRegistry registry) {
        registry.add("batch.input.dir", () -> System.getProperty("batch.input.dir", tempDir.toAbsolutePath().toString()));
    }

    @Test
    void testHeaderOnlyCsv() throws Exception {
        System.setProperty("batch.input.dir", tempDir.toAbsolutePath().toString());
        JobExecution jobExecution = jobOperatorTestUtils.startJob();
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM temperature_records", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testNoCsvFiles() throws Exception {
        System.setProperty("batch.input.dir", tempDir.resolve("empty_dir").toAbsolutePath().toString());
        JobExecution jobExecution = jobOperatorTestUtils.startJob();
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        
        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(0);
    }

    @Test
    void testMissingDirectory() throws Exception {
        System.setProperty("batch.input.dir", tempDir.resolve("non_existent").toAbsolutePath().toString());
        JobExecution jobExecution = jobOperatorTestUtils.startJob();
        
        assertThat(jobExecution.getStatus().isUnsuccessful()).isTrue();
        assertThat(jobExecution.getAllFailureExceptions().get(0).getMessage())
                .contains("Input directory does not exist");
    }
}
