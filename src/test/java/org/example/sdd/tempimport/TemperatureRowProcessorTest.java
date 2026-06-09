package org.example.sdd.tempimport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TemperatureRowProcessorTest {

    private StepCounters stepCounters;
    private TemperatureRowProcessor processor;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    public void setUp() {
        stepCounters = new StepCounters();
        processor = new TemperatureRowProcessor(stepCounters);

        logger = (Logger) LoggerFactory.getLogger(TemperatureRowProcessor.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    public void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    public void testSuccessfulRowProcessing() throws Exception {
        TemperatureRow row = new TemperatureRow("Alice", "2026-06-09T10:00:00", "23.5", "test.csv", 2);
        TemperatureReading reading = processor.process(row);

        assertNotNull(reading);
        assertEquals("Alice", reading.name());
        assertEquals(LocalDateTime.of(2026, 6, 9, 10, 0, 0), reading.recordedAt());
        assertEquals(23.5, reading.temperature());
        assertEquals("test.csv", reading.sourceFile());
        assertEquals(2, reading.sourceLine());
        assertEquals(0, stepCounters.getMalformed());
        assertEquals(0, stepCounters.getDuplicates());
    }

    @Test
    public void testMalformedMissingName() throws Exception {
        TemperatureRow row = new TemperatureRow("", "2026-06-09T10:00:00", "23.5", "test.csv", 2);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(1, stepCounters.getMalformed());
        
        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("test.csv"));
        assertTrue(event.getFormattedMessage().contains("2"));
        assertTrue(event.getFormattedMessage().contains("Missing or blank 'name' column"));
    }

    @Test
    public void testMalformedUnparseableDatetime() throws Exception {
        TemperatureRow row = new TemperatureRow("Alice", "invalid-date", "23.5", "test.csv", 3);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(1, stepCounters.getMalformed());

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        // Verify field order: sourceFile, sourceLine, reason
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("test.csv") && msg.contains("3") && msg.contains("Unparseable datetime"));
    }

    @Test
    public void testIntraRunDuplicate() throws Exception {
        TemperatureRow row1 = new TemperatureRow("Alice", "2026-06-09T10:00:00", "23.5", "test.csv", 2);
        TemperatureRow row2 = new TemperatureRow("Alice", "2026-06-09T10:00:00", "24.0", "test.csv", 3);

        assertNotNull(processor.process(row1));
        assertNull(processor.process(row2));

        assertEquals(1, stepCounters.getDuplicates());
        assertEquals(0, stepCounters.getMalformed());

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        // Verify field order: name, datetime, sourceFile, sourceLine
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("Alice") && msg.contains("2026-06-09T10:00:00") && msg.contains("test.csv") && msg.contains("3"));
    }

    @Test
    public void testBlankLineSkippedSilently() throws Exception {
        TemperatureRow row = new TemperatureRow(null, null, null, "test.csv", 4);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(0, stepCounters.getMalformed());
        assertEquals(0, stepCounters.getDuplicates());
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    public void testMalformedMissingDatetime() throws Exception {
        TemperatureRow row = new TemperatureRow("Alice", "", "23.5", "test.csv", 2);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(1, stepCounters.getMalformed());

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("test.csv") && msg.contains("2") && msg.contains("Missing or blank 'datetime' column"));
    }

    @Test
    public void testMalformedMissingTemp() throws Exception {
        TemperatureRow row = new TemperatureRow("Alice", "2026-06-09T10:00:00", "", "test.csv", 2);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(1, stepCounters.getMalformed());

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("test.csv") && msg.contains("2") && msg.contains("Missing or blank 'temp' column"));
    }

    @Test
    public void testMalformedUnparseableTemp() throws Exception {
        TemperatureRow row = new TemperatureRow("Alice", "2026-06-09T10:00:00", "invalid-temp", "test.csv", 2);
        TemperatureReading reading = processor.process(row);

        assertNull(reading);
        assertEquals(1, stepCounters.getMalformed());

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("test.csv") && msg.contains("2") && msg.contains("Unparseable temperature value"));
    }
}
