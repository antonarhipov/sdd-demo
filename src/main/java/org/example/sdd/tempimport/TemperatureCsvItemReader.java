package org.example.sdd.tempimport;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.core.io.Resource;

public class TemperatureCsvItemReader extends FlatFileItemReader<TemperatureRow> {

    public TemperatureCsvItemReader(Resource resource) {
        super(resource, new HeaderAwareLineMapper(resource.getFilename() != null ? resource.getFilename() : "unknown"));
    }
}
