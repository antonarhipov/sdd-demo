package org.example.sdd.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a validated, type-converted row ready for database insertion.
 *
 * @param name     the name field
 * @param datetime the datetime field as LocalDateTime (parsed from ISO 8601)
 * @param temp     the temperature field as BigDecimal (formatted to 1 decimal place)
 */
public record TemperatureRecord(String name, LocalDateTime datetime, BigDecimal temp) {
}
