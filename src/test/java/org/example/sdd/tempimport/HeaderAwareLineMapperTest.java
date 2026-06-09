package org.example.sdd.tempimport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HeaderAwareLineMapperTest {

    @Test
    public void testSuccessfulMappingWithVaryingColumnOrder() throws Exception {
        HeaderAwareLineMapper mapper = new HeaderAwareLineMapper("test.csv");

        // First non-empty line is parsed as header (returns a placeholder TemperatureRow)
        TemperatureRow headerRow = mapper.mapLine("  temp ,  NAME  ,  DaTeTiMe  , extra_col", 1);
        assertNotNull(headerRow);
        assertNull(headerRow.name());
        assertNull(headerRow.rawDatetime());
        assertNull(headerRow.rawTemp());
        assertEquals("test.csv", headerRow.sourceFile());
        assertEquals(1, headerRow.sourceLine());

        // Second line is parsed as data row
        TemperatureRow dataRow = mapper.mapLine("23.5,Alice,2026-06-09T10:00:00,ignored", 2);
        assertNotNull(dataRow);
        assertEquals("Alice", dataRow.name());
        assertEquals("2026-06-09T10:00:00", dataRow.rawDatetime());
        assertEquals("23.5", dataRow.rawTemp());
        assertEquals("test.csv", dataRow.sourceFile());
        assertEquals(2, dataRow.sourceLine());
    }

    @Test
    public void testMissingRequiredHeaderThrowsException() {
        HeaderAwareLineMapper mapper = new HeaderAwareLineMapper("test.csv");

        // Missing "temp"
        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapLine("name,datetime", 1);
        });
    }

    @Test
    public void testBlankLinesReturnedAsPlaceholder() throws Exception {
        HeaderAwareLineMapper mapper = new HeaderAwareLineMapper("test.csv");

        TemperatureRow blankRow = mapper.mapLine("   ", 1);
        assertNotNull(blankRow);
        assertNull(blankRow.name());
        assertNull(blankRow.rawDatetime());
        assertNull(blankRow.rawTemp());
        assertEquals(1, blankRow.sourceLine());
    }

    @Test
    public void testStateClearedOnLineNumberOne() throws Exception {
        HeaderAwareLineMapper mapper = new HeaderAwareLineMapper("test.csv");

        // Parse a valid header and a data row
        mapper.mapLine("name,datetime,temp", 1);
        TemperatureRow dataRow = mapper.mapLine("Alice,2026-06-09T10:00:00,23.5", 2);
        assertEquals("Alice", dataRow.name());

        // Re-parse with lineNumber == 1 to simulate a new file or run
        mapper.mapLine("temp,datetime,name", 1);
        TemperatureRow dataRow2 = mapper.mapLine("25.0,2026-06-09T11:00:00,Bob", 2);
        assertEquals("Bob", dataRow2.name());
        assertEquals("25.0", dataRow2.rawTemp());
    }
}
