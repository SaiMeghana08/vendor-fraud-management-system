package com.vendorfraud.integration;

import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.scheduler.CoolingOffScheduler;
import com.vendorfraud.vendor.BankAccountStatus;
import com.vendorfraud.vendor.VendorBankAccountRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restart recovery and idempotency.
 *
 * <p>A scheduler pass is modelled exactly as a restart is: the state
 * lives entirely in the database, so "the process was down when the
 * deadline passed" and "the process has just started" are the same
 * situation, and a pass either finds work to do or does not.
 */
class SchedulerRecoveryIntegrationTest extends WorkflowScenario {

    @Autowired private CoolingOffScheduler scheduler;
    @Autowired private VendorBankAccountRepository bankAccountRepository;

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    private void expire(long requestId, long secondsAgo) {
        txTemplate.executeWithoutResult(status -> {
            BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
            ReflectionTestUtils.setField(request, "coolingOffEndAt",
                    Instant.now().minusSeconds(secondsAgo));
            requestRepository.saveAndFlush(request);
        });
    }

    @Test
    @DisplayName("a deadline that passed while the application was down is picked up on the next pass")
    void deadlineMissedWhileDownIsRecovered() throws Exception {
        long requestId = requestInCoolingOff();

        // The window closed two hours ago and nobody was listening.
        expire(requestId, 7200);

        assertThat(scheduler.runOnce()).isEqualTo(1);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATED);
    }

    @Test
    @DisplayName("repeated passes are idempotent: the second finds nothing to do")
    void repeatedPassesAreIdempotent() throws Exception {
        long requestId = requestInCoolingOff();
        expire(requestId, 60);

        assertThat(scheduler.runOnce()).isEqualTo(1);
        assertThat(scheduler.runOnce()).isZero();
        assertThat(scheduler.runOnce()).isZero();

        // Exactly one new banking record was created, not three.
        assertThat(bankAccountRepository.findHistoryByVendor(vendor.getId())).hasSize(2);
        assertThat(bankAccountRepository
                .findByVendorIdAndStatus(vendor.getId(), BankAccountStatus.ACTIVE))
                .isPresent();
    }

    @Test
    @DisplayName("a batch with several due requests processes all of them")
    void processesEveryDueRequestInABatch() throws Exception {
        long firstRequest = requestInCoolingOff();

        // A second vendor, with its own request through to cooling-off.
        var secondVendor = fixtures.createVendorWithBankAccount("VND-4004", "Cobalt IT Consulting");
        String makerToken = tokenFor(maker.getUsername());
        long secondRequest = longField(mockMvc.perform(
                        authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/bank-change-requests"), makerToken)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(json(bankChangeBody(secondVendor.getId()))))
                .andReturn().getResponse().getContentAsString(), "id");
        decide(pendingTask(secondRequest, com.vendorfraud.bankchange.ApprovalStage.FIRST), true,
                "Evidence verified against the vendor letterhead");
        decide(pendingTask(secondRequest, com.vendorfraud.bankchange.ApprovalStage.SECOND), true,
                "Independently verified by a second reviewer");

        expire(firstRequest, 300);
        expire(secondRequest, 120);

        assertThat(scheduler.runOnce()).isEqualTo(2);
        assertThat(requestRepository.findById(firstRequest).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATED);
        assertThat(requestRepository.findById(secondRequest).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATED);
    }

    @Test
    @DisplayName("revalidation blocks activation when the vendor was deactivated during the window")
    void vendorDeactivatedDuringWindowBlocksActivation() throws Exception {
        long requestId = requestInCoolingOff();

        txTemplate.executeWithoutResult(status -> {
            var v = vendorRepository.findById(vendor.getId()).orElseThrow();
            v.deactivate(null);
            vendorRepository.saveAndFlush(v);
        });
        expire(requestId, 60);

        scheduler.runOnce();

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(BankChangeStatus.ACTIVATION_BLOCKED);
        assertThat(request.getTerminalReason()).contains("inactive");

        // Nothing was activated, and the original banking record stands.
        assertThat(bankAccountRepository.findHistoryByVendor(vendor.getId())).hasSize(1);
    }

    @Test
    @DisplayName("a blocked request is not retried into activation by later passes")
    void blockedRequestIsNotSilentlyActivatedLater() throws Exception {
        long requestId = requestInCoolingOff();
        txTemplate.executeWithoutResult(status -> {
            var v = vendorRepository.findById(vendor.getId()).orElseThrow();
            v.deactivate(null);
            vendorRepository.saveAndFlush(v);
        });
        expire(requestId, 60);
        scheduler.runOnce();

        // Later passes leave it blocked: resolving it is a human decision.
        scheduler.runOnce();
        scheduler.runOnce();

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATION_BLOCKED);
    }

    @Autowired
    private com.vendorfraud.vendor.VendorRepository vendorRepository;
}
