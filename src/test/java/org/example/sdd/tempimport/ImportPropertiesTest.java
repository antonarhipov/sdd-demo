package org.example.sdd.tempimport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportPropertiesTest {

    @Test
    void testDefaultValue() {
        ImportProperties properties = new ImportProperties();
        assertEquals("./data/input", properties.getInputDir());
    }

    @Test
    void testValidInput() {
        ImportProperties properties = new ImportProperties();
        properties.setInputDir("custom/path");
        assertEquals("custom/path", properties.getInputDir());
    }

    @Test
    void testEmptyValueThrowsException() {
        ImportProperties properties = new ImportProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setInputDir(""));
        assertThrows(IllegalArgumentException.class, () -> properties.setInputDir("   "));
        assertThrows(IllegalArgumentException.class, () -> properties.setInputDir(null));
    }
}
