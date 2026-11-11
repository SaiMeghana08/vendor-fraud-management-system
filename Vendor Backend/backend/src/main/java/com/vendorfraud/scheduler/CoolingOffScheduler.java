package com.vendorfraud.scheduler;

import com.vendorfraud.bankchange.BankActivationService;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeRequestRepository;
import com.vendorfraud.config.CoolingOffProperties;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Watches cooling-off deadlines.
 *
 * <p>Restart recovery is a property of the design rather than an extra
 * mechanism: the deadline lives in the database, and each run asks
 * "which requests are in COOLING_OFF with a deadline in the past?".
 * A process that was down for an hour simply finds more rows on its
 * next run; nothing is missed and nothing needs replaying.
 *
 * <p>Each request is handled in its own transaction under a row lock,
 * which makes the whole pass idempotent and keeps one bad row from
 * poisoning the batch. A request that no longer qualifies is skipped.
 */
@Component
public class CoolingOffScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoolingOffScheduler.class);

    private final BankChangeRequestRepository requestRepository;
    private final BankActivationService activationService;
    private final CoolingOffProperties properties;

    public CoolingOffScheduler(BankChangeRequestRepository requestRepository,
                               BankActivationService activationService,
                               CoolingOffProperties properties) {
        this.requestRepository = requestRepository;
        this.activationService = activationService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.cooling-off.scheduler.cron:0 */2 * * * *}")
    public void processDueCoolingOffWindows() {
        try {
            int processed = runOnce();
            if (processed > 0) {
                log.info("Cooling-off pass complete: {} request(s) handled", processed);
            }
        } catch (Exception ex) {
            // A scheduled task must never die on an unexpected failure:
            // the next run has to happen regardless.
            log.error("Cooling-off pass failed; the next scheduled run will retry", ex);
        }
    }

    /** Separated from the schedule so tests can drive a single pass. */
    public int runOnce() {
        List<BankChangeRequest> due = findDue();
        if (due.isEmpty()) {
            return 0;
        }
        log.info("Cooling-off pass found {} due request(s)", due.size());

        AtomicInteger handled = new AtomicInteger();
        for (BankChangeRequest request : due) {
            Long id = request.getId();
            try {
                BankActivationService.Outcome outcome = activationService.revalidateAndActivate(id);
                if (outcome != BankActivationService.Outcome.SKIPPED) {
                    handled.incrementAndGet();
                }
            } catch (Exception ex) {
                // Isolate the failure: log it, leave the request where
                // it is, carry on with the rest of the batch.
                log.error("Failed to process cooling-off completion for request id {}", id, ex);
            }
        }
        return handled.get();
    }

    @Transactional(readOnly = true)
    protected List<BankChangeRequest> findDue() {
        return requestRepository.findCoolingOffDue(Instant.now(),
                PageRequest.of(0, properties.scheduler().batchSize()));
    }
}
