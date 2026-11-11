package com.vendorfraud.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * One row per reference family. The row is the lock: callers take a
 * pessimistic write lock on it, read {@code nextValue}, increment and
 * commit, which makes concurrent generation safe without ever looking
 * at MAX(id).
 */
@Entity
@Table(name = "reference_sequences")
public class ReferenceSequence {

    @Id
    @Column(name = "sequence_name", nullable = false, length = 40)
    private String sequenceName;

    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    @Column(name = "year_part", nullable = false)
    private int yearPart;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReferenceSequence() {
    }

    /**
     * Returns the current value and advances the counter. Rolling the
     * year restarts numbering at one.
     */
    long takeNext(int currentYear) {
        if (currentYear != this.yearPart) {
            this.yearPart = currentYear;
            this.nextValue = 1L;
        }
        long value = this.nextValue;
        this.nextValue = value + 1L;
        return value;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getYearPart() {
        return yearPart;
    }

    public long getNextValue() {
        return nextValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
