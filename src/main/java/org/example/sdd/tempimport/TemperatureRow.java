package org.example.sdd.tempimport;

public record TemperatureRow(
    String name,
    String rawDatetime,
    String rawTemp,
    String sourceFile,
    int sourceLine
) {
}
