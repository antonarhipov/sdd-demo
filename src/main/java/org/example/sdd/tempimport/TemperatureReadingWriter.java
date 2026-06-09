package org.example.sdd.tempimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
@StepScope
public class TemperatureReadingWriter implements ItemWriter<TemperatureReading> {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureReadingWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final StepCounters stepCounters;
    private Boolean useBatch = null;

    public TemperatureReadingWriter(JdbcTemplate jdbcTemplate, StepCounters stepCounters) {
        this.jdbcTemplate = jdbcTemplate;
        this.stepCounters = stepCounters;
    }

    private synchronized boolean shouldStepUseBatch() {
        if (useBatch != null) {
            return useBatch;
        }
        try {
            var ds = jdbcTemplate.getDataSource();
            if (ds != null) {
                try (var conn = ds.getConnection()) {
                    String url = conn.getMetaData().getURL();
                    if (url != null && url.toLowerCase().contains("rewritebatchedstatements=true")) {
                        logger.info("Detected rewriteBatchedStatements=true in JDBC URL. Disabling batch updates to keep per-row counts observable.");
                        useBatch = false;
                        return useBatch;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to inspect database metadata for batch rewrite support", e);
        }
        useBatch = true;
        return useBatch;
    }

    @Override
    public void write(Chunk<? extends TemperatureReading> chunk) throws Exception {
        List<? extends TemperatureReading> items = chunk.getItems();
        if (items.isEmpty()) {
            return;
        }

        String sql = "INSERT IGNORE INTO temperature_reading (name, recorded_at, temperature) VALUES (?, ?, ?)";

        if (shouldStepUseBatch()) {
            int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    TemperatureReading r = items.get(i);
                    ps.setString(1, r.name());
                    ps.setTimestamp(2, Timestamp.valueOf(r.recordedAt()));
                    ps.setDouble(3, r.temperature());
                }

                @Override
                public int getBatchSize() {
                    return items.size();
                }
            });

            // Derive inserted and duplicates from affected-row counts
            for (int i = 0; i < results.length; i++) {
                int affected = results[i];
                TemperatureReading r = items.get(i);

                if (affected == 1) {
                    stepCounters.addInserted(1);
                } else if (affected == 0) {
                    stepCounters.incrementDuplicates();
                    logger.warn("Duplicate record: {}, {}, {}, {}",
                            r.name(), r.recordedAt().toString(), r.sourceFile(), r.sourceLine());
                } else if (affected == java.sql.Statement.SUCCESS_NO_INFO) {
                    stepCounters.addInserted(1);
                }
            }
        } else {
            // Per-row executes to ensure per-row counts stay observable
            for (TemperatureReading r : items) {
                int affected = jdbcTemplate.update(sql,
                        r.name(),
                        Timestamp.valueOf(r.recordedAt()),
                        r.temperature()
                );
                if (affected == 1) {
                    stepCounters.addInserted(1);
                } else if (affected == 0) {
                    stepCounters.incrementDuplicates();
                    logger.warn("Duplicate record: {}, {}, {}, {}",
                            r.name(), r.recordedAt().toString(), r.sourceFile(), r.sourceLine());
                }
            }
        }
    }
}
