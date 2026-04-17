package org.example.sdd.batch;

import org.example.sdd.model.TemperatureRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writer that handles duplicate detection and persistence.
 * Checks for existing (name, datetime) records before each insert.
 */
@Component
public class TemperatureItemWriter implements ItemWriter<TemperatureRecord>, StepExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(TemperatureItemWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private StepExecution stepExecution;

    private int insertedCount = 0;
    private int duplicateCount = 0;

    public TemperatureItemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        // Optionally restore counts from execution context if needed for restartability
        this.insertedCount = stepExecution.getExecutionContext().getInt("insertedCount", 0);
        this.duplicateCount = stepExecution.getExecutionContext().getInt("duplicateCount", 0);
    }

    @Override
    public void write(Chunk<? extends TemperatureRecord> chunk) throws Exception {
        for (TemperatureRecord record : chunk) {
            if (checkIfRecordExists(record)) {
                log.info("Duplicate record detected: {} at {}", record.name(), record.datetime());
                duplicateCount++;
            } else {
                insertRecord(record);
                insertedCount++;
            }
        }
        updateExecutionContext();
    }

    private boolean checkIfRecordExists(TemperatureRecord record) {
        String sql = "SELECT COUNT(*) FROM temperature_records WHERE name = ? AND datetime = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, record.name(), record.datetime());
        return count != null && count > 0;
    }

    private void insertRecord(TemperatureRecord record) {
        String sql = "INSERT INTO temperature_records (name, datetime, temp) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, record.name(), record.datetime(), record.temp());
    }

    private void updateExecutionContext() {
        if (stepExecution != null) {
            stepExecution.getExecutionContext().putInt("insertedCount", insertedCount);
            stepExecution.getExecutionContext().putInt("duplicateCount", duplicateCount);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        updateExecutionContext();
        return null;
    }
}
