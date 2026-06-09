package org.example.sdd.tempimport;

import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class DynamicRunIdJobParametersConverter extends DefaultJobParametersConverter {

    @Override
    public JobParameters getJobParameters(Properties properties) {
        JobParameters params = super.getJobParameters(properties);
        return new JobParametersBuilder(params)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
    }
}
