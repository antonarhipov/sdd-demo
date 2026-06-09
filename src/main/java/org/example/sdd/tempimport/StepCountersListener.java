package org.example.sdd.tempimport;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class StepCountersListener implements StepExecutionListener {

    private final StepCounters stepCounters;

    public StepCountersListener(StepCounters stepCounters) {
        this.stepCounters = stepCounters;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // No-op
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        var context = stepExecution.getExecutionContext();
        context.put("inserted", stepCounters.getInserted());
        context.put("duplicates", stepCounters.getDuplicates());
        context.put("malformed", stepCounters.getMalformed());
        return stepExecution.getExitStatus();
    }
}
