package org.example.sdd.tempimport;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class TemperatureImportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CsvFileDiscoverer csvFileDiscoverer;
    private final TemperatureRowProcessor processor;
    private final TemperatureReadingWriter writer;
    private final StepCountersListener stepCountersListener;

    public TemperatureImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CsvFileDiscoverer csvFileDiscoverer,
            TemperatureRowProcessor processor,
            TemperatureReadingWriter writer,
            StepCountersListener stepCountersListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvFileDiscoverer = csvFileDiscoverer;
        this.processor = processor;
        this.writer = writer;
        this.stepCountersListener = stepCountersListener;
    }

    @Bean
    public Job temperatureImportJob() {
        List<Path> files = csvFileDiscoverer.discoverFiles();
        JobBuilder jobBuilder = new JobBuilder("temperatureImportJob", jobRepository)
                .incrementer(new RunIdIncrementer());

        if (files.isEmpty()) {
            Step noOpStep = new StepBuilder("noOpStep", jobRepository)
                    .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                    .build();
            return jobBuilder.start(noOpStep).build();
        }

        Step firstStep = null;
        FlowBuilder<Flow> flowBuilder = null;

        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String filename = file.getFileName().toString();
            String stepName = "importStep_" + filename.replace('.', '_');

            TemperatureCsvItemReader reader = new TemperatureCsvItemReader(new FileSystemResource(file));

            Step step = new StepBuilder(stepName, jobRepository)
                    .<TemperatureRow, TemperatureReading>chunk(1000)
                    .transactionManager(transactionManager)
                    .reader(reader)
                    .processor(processor)
                    .writer(writer)
                    .listener(stepCountersListener)
                    .build();

            if (i == 0) {
                firstStep = step;
                flowBuilder = new FlowBuilder<Flow>("importFlow_" + filename.replace('.', '_')).start(step);
            } else {
                flowBuilder.on("*").to(step);
            }
        }

        if (firstStep == null) {
            Step noOpStep = new StepBuilder("noOpStep", jobRepository)
                    .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                    .build();
            return jobBuilder.start(noOpStep).build();
        }

        return jobBuilder.start(flowBuilder.build()).end().build();
    }
}
