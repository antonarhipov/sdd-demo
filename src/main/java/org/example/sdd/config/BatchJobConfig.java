package org.example.sdd.config;

import org.example.sdd.batch.TemperatureItemProcessor;
import org.example.sdd.batch.TemperatureItemWriter;
import org.example.sdd.listener.BatchJobListener;
import org.example.sdd.listener.MalformedRowSkipListener;
import org.example.sdd.model.CsvRow;
import org.example.sdd.model.TemperatureRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MultiResourceItemReader<CsvRow> reader;
    private final TemperatureItemWriter writer;
    private final BatchJobListener jobListener;
    private final MalformedRowSkipListener skipListener;

    public BatchJobConfig(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          MultiResourceItemReader<CsvRow> reader,
                          TemperatureItemWriter writer,
                          BatchJobListener jobListener,
                          MalformedRowSkipListener skipListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.reader = reader;
        this.writer = writer;
        this.jobListener = jobListener;
        this.skipListener = skipListener;
    }

    @Bean
    public TemperatureItemProcessor processor() {
        return new TemperatureItemProcessor();
    }

    @Bean
    public Job importTemperatureJob() {
        return new JobBuilder("importTemperatureJob", jobRepository)
                .listener(jobListener)
                .start(importTemperatureStep())
                .build();
    }

    @Bean
    public Step importTemperatureStep() {
        TemperatureItemProcessor processor = processor();
        return new StepBuilder("importTemperatureStep", jobRepository)
                .<CsvRow, TemperatureRecord>chunk(100)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(Integer.MAX_VALUE)
                .skipPolicy((t, skipCount) -> !(t instanceof org.springframework.dao.DataAccessException))
                .listener(processor)
                .listener(writer)
                .listener(skipListener)
                .build();
    }
}
