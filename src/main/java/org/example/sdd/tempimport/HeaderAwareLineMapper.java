package org.example.sdd.tempimport;

import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

import java.util.HashMap;
import java.util.Map;

public class HeaderAwareLineMapper implements LineMapper<TemperatureRow> {

    private final String sourceFile;
    private final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
    private final Map<String, Integer> headerIndices = new HashMap<>();

    public HeaderAwareLineMapper(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public TemperatureRow mapLine(String line, int lineNumber) throws Exception {
        if (lineNumber == 1) {
            headerIndices.clear();
        }
        if (line == null || line.trim().isEmpty()) {
            return new TemperatureRow(null, null, null, sourceFile, lineNumber);
        }

        FieldSet fieldSet = tokenizer.tokenize(line);
        String[] tokens = fieldSet.getValues();

        if (headerIndices.isEmpty()) {
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token != null) {
                    headerIndices.put(token.trim().toLowerCase(), i);
                }
            }
            if (!headerIndices.containsKey("name") ||
                !headerIndices.containsKey("datetime") ||
                !headerIndices.containsKey("temp")) {
                throw new IllegalArgumentException("Missing required header in file " + sourceFile);
            }
            return new TemperatureRow(null, null, null, sourceFile, lineNumber);
        }

        Integer nameIdx = headerIndices.get("name");
        Integer datetimeIdx = headerIndices.get("datetime");
        Integer tempIdx = headerIndices.get("temp");

        String name = (nameIdx != null && nameIdx < tokens.length) ? tokens[nameIdx] : null;
        String rawDatetime = (datetimeIdx != null && datetimeIdx < tokens.length) ? tokens[datetimeIdx] : null;
        String rawTemp = (tempIdx != null && tempIdx < tokens.length) ? tokens[tempIdx] : null;

        return new TemperatureRow(name, rawDatetime, rawTemp, sourceFile, lineNumber);
    }

    // Exposed for testing/inspection if needed
    public Map<String, Integer> getHeaderIndices() {
        return new HashMap<>(headerIndices);
    }
}
