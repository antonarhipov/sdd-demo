package org.example.sdd.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Listener for the batch job. Validates input directory and prints summary.
 */
@Component
@JobScope
public class BatchJobListener implements JobExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(BatchJobListener.class);

    @Value("${batch.input.dir}")
    private String inputDir;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        File dir = new File(inputDir);
        if (!dir.exists() || !dir.isDirectory()) {
            // Throwing RuntimeException as beforeJob cannot throw checked JobExecutionException
            throw new RuntimeException("Input directory does not exist: " + inputDir);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int totalInserted = 0;
        int totalDuplicates = 0;
        int totalMalformed = 0;

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            totalInserted += stepExecution.getExecutionContext().getInt("insertedCount", 0);
            totalDuplicates += stepExecution.getExecutionContext().getInt("duplicateCount", 0);
            totalMalformed += stepExecution.getExecutionContext().getInt("malformedCount", 0);
        }

        log.info("Batch complete — status: {}, inserted: {}, duplicates: {}, malformed: {}",
                jobExecution.getStatus(), totalInserted, totalDuplicates, totalMalformed);
    }
}
