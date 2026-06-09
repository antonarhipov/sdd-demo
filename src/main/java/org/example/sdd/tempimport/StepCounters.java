package org.example.sdd.tempimport;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class StepCounters {

    private int inserted = 0;
    private int duplicates = 0;
    private int malformed = 0;

    public synchronized int getInserted() {
        return inserted;
    }

    public synchronized void addInserted(int count) {
        this.inserted += count;
    }

    public synchronized int getDuplicates() {
        return duplicates;
    }

    public synchronized void incrementDuplicates() {
        this.duplicates++;
    }

    public synchronized void addDuplicates(int count) {
        this.duplicates += count;
    }

    public synchronized int getMalformed() {
        return malformed;
    }

    public synchronized void incrementMalformed() {
        this.malformed++;
    }
}
