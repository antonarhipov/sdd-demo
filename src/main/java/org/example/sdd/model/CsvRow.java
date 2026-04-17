package org.example.sdd.model;

/**
 * Represents a raw row from the CSV input file before any type conversion or validation.
 *
 * @param name     the name field from the CSV
 * @param datetime the datetime field from the CSV as a raw String
 * @param temp     the temperature field from the CSV as a raw String
 */
public record CsvRow(String name, String datetime, String temp) {
}
