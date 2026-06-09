package org.example.sdd.tempimport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    private String inputDir = "./data/input";

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        if (inputDir == null || inputDir.trim().isEmpty()) {
            throw new IllegalArgumentException("app.import.input-dir must not be empty");
        }
        this.inputDir = inputDir;
    }

    @PostConstruct
    public void validate() {
        if (inputDir == null || inputDir.trim().isEmpty()) {
            throw new IllegalArgumentException("app.import.input-dir must not be empty");
        }
    }
}
