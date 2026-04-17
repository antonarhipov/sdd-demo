package org.example.sdd.listener;

import org.example.sdd.model.CsvRow;
import org.example.sdd.model.TemperatureRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Listener that tracks malformed rows skipped during processing or reading.
 * Note: If the processor returns null (as per task-3.2), this listener's 
 * onSkipInProcess method might not be triggered by Spring Batch.
 */
@Component
public class MalformedRowSkipListener implements SkipListener<CsvRow, TemperatureRecord>, StepExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(MalformedRowSkipListener.class);

    private StepExecution stepExecution;
    private int malformedCount = 0;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        this.malformedCount = stepExecution.getExecutionContext().getInt("malformedCount", 0);
        // Ensure it's present in context even if 0
        stepExecution.getExecutionContext().putInt("malformedCount", malformedCount);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        incrementCount();
        log.warn("Malformed row skipped during read. Reason: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(CsvRow item, Throwable t) {
        incrementCount();
        log.warn("Malformed row skipped during processing: {}. Reason: {}", item, t.getMessage());
    }

    private void incrementCount() {
        malformedCount++;
        if (stepExecution != null) {
            stepExecution.getExecutionContext().putInt("malformedCount", malformedCount);
        }
    }
}
