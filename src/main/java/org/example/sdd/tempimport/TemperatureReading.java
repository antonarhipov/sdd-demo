package org.example.sdd.tempimport;

import java.time.LocalDateTime;

public record TemperatureReading(String name, LocalDateTime recordedAt, double temperature, String sourceFile, int sourceLine) {
}
