package org.example.sdd.tempimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

@Component
@StepScope
public class TemperatureRowProcessor implements ItemProcessor<TemperatureRow, TemperatureReading> {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureRowProcessor.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final StepCounters stepCounters;
    private final Set<NameRecordedAt> seenKeys = new HashSet<>();

    public TemperatureRowProcessor(StepCounters stepCounters) {
        this.stepCounters = stepCounters;
    }

    @Override
    public TemperatureReading process(TemperatureRow item) throws Exception {
        // Skip blank lines / header placeholders silently (all fields null)
        if (item.name() == null && item.rawDatetime() == null && item.rawTemp() == null) {
            return null;
        }

        // Validate missing/blank required columns
        if (item.name() == null || item.name().trim().isEmpty()) {
            logMalformed(item.sourceFile(), item.sourceLine(), "Missing or blank 'name' column");
            return null;
        }
        if (item.rawDatetime() == null || item.rawDatetime().trim().isEmpty()) {
            logMalformed(item.sourceFile(), item.sourceLine(), "Missing or blank 'datetime' column");
            return null;
        }
        if (item.rawTemp() == null || item.rawTemp().trim().isEmpty()) {
            logMalformed(item.sourceFile(), item.sourceLine(), "Missing or blank 'temp' column");
            return null;
        }

        // Parse temp
        double temp;
        try {
            temp = Double.parseDouble(item.rawTemp().trim());
        } catch (NumberFormatException e) {
            logMalformed(item.sourceFile(), item.sourceLine(), "Unparseable temperature value: " + item.rawTemp());
            return null;
        }

        // Parse datetime
        LocalDateTime recordedAt;
        try {
            recordedAt = LocalDateTime.parse(item.rawDatetime().trim(), DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            logMalformed(item.sourceFile(), item.sourceLine(), "Unparseable datetime value: " + item.rawDatetime());
            return null;
        }

        // Check intra-run duplicate
        NameRecordedAt key = new NameRecordedAt(item.name().trim(), recordedAt);
        if (seenKeys.contains(key)) {
            logger.warn("Duplicate record: {}, {}, {}, {}",
                    item.name().trim(), item.rawDatetime().trim(), item.sourceFile(), item.sourceLine());
            stepCounters.incrementDuplicates();
            return null;
        }

        seenKeys.add(key);
        return new TemperatureReading(item.name().trim(), recordedAt, temp, item.sourceFile(), item.sourceLine());
    }

    private void logMalformed(String sourceFile, int sourceLine, String reason) {
        logger.warn("Malformed record: {}, {}, {}", sourceFile, sourceLine, reason);
        stepCounters.incrementMalformed();
    }
}
