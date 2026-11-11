package com.vendorfraud.reference;

import com.vendorfraud.common.exception.ResourceNotFoundException;
import java.time.Year;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates every business reference in the system. No client is ever
 * permitted to supply one.
 *
 * <p>Each call runs in its own transaction
 * ({@code Propagation.REQUIRES_NEW}) so the row lock is held for
 * microseconds rather than for the life of the surrounding business
 * transaction. A number consumed by a transaction that later rolls back
 * leaves a gap; references are identifiers, not a count, so gaps are
 * acceptable while duplicates are not. The unique constraints on
 * {@code request_reference}, {@code payment_reference} and
 * {@code override_reference} are the final guarantee.
 */
@Service
public class ReferenceGeneratorService {

    public static final String SEQ_BANK_CHANGE_REQUEST = "BANK_CHANGE_REQUEST";
    public static final String SEQ_PAYMENT = "PAYMENT";
    public static final String SEQ_EMERGENCY_OVERRIDE = "EMERGENCY_OVERRIDE";

    private final ReferenceSequenceRepository repository;

    public ReferenceGeneratorService(ReferenceSequenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Each entry point carries its own {@code REQUIRES_NEW} so that the
     * transactional proxy is applied however the service is called.
     * Delegating to a non-annotated sibling method would bypass the
     * proxy and silently join the caller's transaction instead.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextBankChangeRequestReference() {
        return generate(SEQ_BANK_CHANGE_REQUEST);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextPaymentReference() {
        return generate(SEQ_PAYMENT);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextEmergencyOverrideReference() {
        return generate(SEQ_EMERGENCY_OVERRIDE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String sequenceName) {
        return generate(sequenceName);
    }

    private String generate(String sequenceName) {
        ReferenceSequence sequence = repository.findByNameForUpdate(sequenceName)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceSequence", sequenceName));
        int year = Year.now().getValue();
        long value = sequence.takeNext(year);
        repository.save(sequence);
        return "%s-%d-%06d".formatted(sequence.getPrefix(), sequence.getYearPart(), value);
    }
}
