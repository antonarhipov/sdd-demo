package org.example.sdd.batch;

import org.example.sdd.model.CsvRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

/**
 * Configuration for the CSV item reader.
 * Uses a MultiResourceItemReader to read all CSV files from a configured directory.
 */
@Configuration
public class CsvItemReader {
    private static final Logger log = LoggerFactory.getLogger(CsvItemReader.class);

    @Bean
    @StepScope
    public MultiResourceItemReader<CsvRow> multiResourceItemReader(@Value("${batch.input.dir}") String inputDir) {
        Resource[] resources = null;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String locationPattern = "file:" + inputDir + "/*.csv";
            resources = resolver.getResources(locationPattern);
        } catch (IOException e) {
            log.error("Error while resolving resources from {}", inputDir, e);
        }

        if (resources == null || resources.length == 0) {
            log.warn("No .csv files found in directory: {}", inputDir);
            resources = new Resource[0];
        }

        return new MultiResourceItemReaderBuilder<CsvRow>()
                .name("multiResourceReader")
                .resources(resources)
                .delegate(csvRowFlatFileItemReader())
                .build();
    }

    private FlatFileItemReader<CsvRow> csvRowFlatFileItemReader() {
        return new FlatFileItemReaderBuilder<CsvRow>()
                .name("csvRowReader")
                .delimited()
                .names("name", "datetime", "temp")
                .targetType(CsvRow.class)
                .linesToSkip(1) // AC-5.4/5.5: Skip header row
                .strict(false) // AC-2.2: Extra columns ignored
                .build();
    }
}
