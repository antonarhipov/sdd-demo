package org.example.sdd.tempimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileDispositionTasklet implements Tasklet {

    private static final Logger logger = LoggerFactory.getLogger(FileDispositionTasklet.class);

    private final Path sourceFile;
    private final String importStepName;

    public FileDispositionTasklet(Path sourceFile, String importStepName) {
        this.sourceFile = sourceFile;
        this.importStepName = importStepName;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StepExecution currentStepExecution = chunkContext.getStepContext().getStepExecution();
        JobExecution jobExecution = currentStepExecution.getJobExecution();

        StepExecution importStepExecution = null;
        for (StepExecution se : jobExecution.getStepExecutions()) {
            if (se.getStepName().equals(importStepName)) {
                importStepExecution = se;
                break;
            }
        }

        if (importStepExecution == null) {
            throw new IllegalStateException("Could not find step execution for " + importStepName);
        }

        ExitStatus exitStatus = importStepExecution.getExitStatus();
        boolean isSuccess = exitStatus.getExitCode().equals(ExitStatus.COMPLETED.getExitCode());

        Path inputDir = sourceFile.getParent();
        Path destinationDir = isSuccess ? inputDir.resolveSibling("processed") : inputDir.resolveSibling("failed");

        Files.createDirectories(destinationDir);
        Path targetPath = destinationDir.resolve(sourceFile.getFileName());

        logger.info("Moving file {} to {}", sourceFile, targetPath);
        try {
            Files.move(sourceFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return RepeatStatus.FINISHED;
    }
}
