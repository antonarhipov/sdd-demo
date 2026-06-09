package org.example.sdd.tempimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

import java.nio.file.Path;
import java.util.List;

public class ImportSummaryListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(ImportSummaryListener.class);

    private final List<Path> files;

    public ImportSummaryListener(List<Path> files) {
        this.files = files;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // No-op
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int totalInserted = 0;
        int totalDuplicates = 0;
        int totalMalformed = 0;
        int filesProcessed = 0;
        int filesFailed = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Temperature Import Job Summary:\n");

        for (Path file : files) {
            String filename = file.getFileName().toString();
            String stepName = "importStep_" + filename.replace('.', '_');
            StepExecution se = findStepExecution(jobExecution, stepName);

            if (se != null) {
                var ec = se.getExecutionContext();
                int ins = ec.containsKey("inserted") ? ec.getInt("inserted") : 0;
                int dup = ec.containsKey("duplicates") ? ec.getInt("duplicates") : 0;
                int mal = ec.containsKey("malformed") ? ec.getInt("malformed") : 0;

                totalInserted += ins;
                totalDuplicates += dup;
                totalMalformed += mal;

                String status = se.getExitStatus().getExitCode();
                if (status.equals(ExitStatus.COMPLETED.getExitCode())) {
                    filesProcessed++;
                } else {
                    filesFailed++;
                }

                sb.append(String.format("File: %s - Status: %s, Inserted: %d, Duplicates: %d, Malformed: %d\n",
                        filename, status, ins, dup, mal));
            } else {
                sb.append(String.format("File: %s - Status: NOT_RUN, Inserted: 0, Duplicates: 0, Malformed: 0\n", filename));
            }
        }

        sb.append(String.format("Grand Totals - Inserted: %d, Duplicates: %d, Malformed: %d, Files Processed: %d, Files Failed: %d",
                totalInserted, totalDuplicates, totalMalformed, filesProcessed, filesFailed));

        logger.info(sb.toString());
    }

    private StepExecution findStepExecution(JobExecution jobExecution, String stepName) {
        for (StepExecution se : jobExecution.getStepExecutions()) {
            if (se.getStepName().equals(stepName)) {
                return se;
            }
        }
        return null;
    }
}
