package org.example.sdd.tempimport;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CsvFileDiscoverer {

    private final ImportProperties importProperties;

    public CsvFileDiscoverer(ImportProperties importProperties) {
        this.importProperties = importProperties;
    }

    public List<Path> discoverFiles() {
        Path inputPath = Paths.get(importProperties.getInputDir());
        if (!Files.exists(inputPath) || !Files.isDirectory(inputPath)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(inputPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".csv");
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.naturalOrder()))
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover CSV files in " + inputPath, e);
        }
    }
}
