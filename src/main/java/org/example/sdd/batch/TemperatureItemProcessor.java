package org.example.sdd.batch;

import org.example.sdd.model.CsvRow;
import org.example.sdd.model.TemperatureRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Processor that converts raw CsvRow to TemperatureRecord.
 * Performs type conversion and validation.
 */
public class TemperatureItemProcessor implements ItemProcessor<CsvRow, TemperatureRecord>, StepExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(TemperatureItemProcessor.class);
    private StepExecution stepExecution;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public TemperatureRecord process(CsvRow item) {
        if (item.name() == null || item.name().isBlank() ||
                item.datetime() == null || item.datetime().isBlank() ||
                item.temp() == null || item.temp().isBlank()) {
            log.warn("Skipping malformed row: missing required fields. Row: {}", item);
            incrementMalformedCount();
            return null;
        }

        LocalDateTime datetime;
        try {
            datetime = LocalDateTime.parse(item.datetime());
        } catch (DateTimeParseException e) {
            log.warn("Skipping malformed row: invalid datetime format '{}'. Row: {}", item.datetime(), item);
            incrementMalformedCount();
            return null;
        }

        BigDecimal temp;
        try {
            temp = new BigDecimal(item.temp()).setScale(1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Skipping malformed row: invalid temperature value '{}'. Row: {}", item.temp(), item);
            incrementMalformedCount();
            return null;
        }

        return new TemperatureRecord(item.name(), datetime, temp);
    }

    private void incrementMalformedCount() {
        if (stepExecution != null) {
            int count = stepExecution.getExecutionContext().getInt("malformedCount", 0);
            stepExecution.getExecutionContext().putInt("malformedCount", count + 1);
        }
    }
}
