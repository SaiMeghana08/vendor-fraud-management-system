package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.scheduler.CoolingOffScheduler;
import com.vendorfraud.vendor.BankAccountStatus;
import com.vendorfraud.vendor.VendorBankAccount;
import com.vendorfraud.vendor.VendorBankAccountRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The mandatory workflow, end to end. */
class BankChangeWorkflowIntegrationTest extends WorkflowScenario {

    @Autowired private CoolingOffScheduler scheduler;
    @Autowired private VendorBankAccountRepository bankAccountRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    @Test
    @DisplayName("creation generates a reference, derives the Maker and leaves live banking untouched")
    void creationDoesNotTouchLiveBankingDetails() throws Exception {
        String body = createRequestAsMaker();

        assertThat(stringField(body, "requestReference")).matches("BCR-\\d{4}-\\d{6}");
        assertThat(longField(body, "makerId")).isEqualTo(maker.getId());
        assertThat(stringField(body, "status")).isEqualTo("PENDING_FIRST_APPROVAL");
        // Both references are masked on the way out.
        assertThat(stringField(body, "proposedMaskedAccountReference")).endsWith("0777");
        assertThat(stringField(body, "proposedMaskedAccountReference")).doesNotContain("SYN-AC");

        VendorBankAccount live = bankAccountRepository
                .findByVendorIdAndStatus(vendor.getId(), BankAccountStatus.ACTIVE).orElseThrow();
        assertThat(live.getAccountReference()).doesNotContain("200777");
    }

    @Test
    @DisplayName("a first approver is assigned automatically and is never the Maker")
    void firstApproverAssignedAutomatically() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");

        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        assertThat(first.assigneeId()).isNotEqualTo(maker.getId());
        assertThat(first.assigneeId()).isIn(approverOne.getId(), approverTwo.getId(),
                approverThree.getId());
        // No second-stage task exists yet: the stages are sequential.
        assertThat(taskRefs(requestId)).hasSize(1);
    }

    @Test
    @DisplayName("first approval assigns a different second approver")
    void secondApproverIsADifferentPerson() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        decide(first, true, "Evidence verified against the vendor letterhead");

        TaskRef second = pendingTask(requestId, ApprovalStage.SECOND);
        assertThat(second.assigneeId())
                .isNotEqualTo(first.assigneeId())
                .isNotEqualTo(maker.getId());
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.PENDING_SECOND_APPROVAL);
    }

    @Test
    @DisplayName("the second approval starts cooling-off and leaves the details inactive")
    void secondApprovalStartsCoolingOff() throws Exception {
        long requestId = requestInCoolingOff();

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(BankChangeStatus.COOLING_OFF);
        assertThat(request.getCoolingOffStartAt()).isNotNull();
        assertThat(request.getCoolingOffEndAt()).isAfter(Instant.now());

        // Still inactive: the vendor's live record has not moved.
        VendorBankAccount live = bankAccountRepository
                .findByVendorIdAndStatus(vendor.getId(), BankAccountStatus.ACTIVE).orElseThrow();
        assertThat(live.getAccountReference()).doesNotContain("200777");
    }

    @Test
    @DisplayName("the scheduler will not activate before the deadline")
    void schedulerDoesNotActivateEarly() throws Exception {
        long requestId = requestInCoolingOff();

        int handled = scheduler.runOnce();

        assertThat(handled).isZero();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.COOLING_OFF);
    }

    @Test
    @DisplayName("once the deadline passes the scheduler activates and preserves history")
    void schedulerActivatesAfterDeadlineAndPreservesHistory() throws Exception {
        long requestId = requestInCoolingOff();
        VendorBankAccount originalLive = bankAccountRepository
                .findByVendorIdAndStatus(vendor.getId(), BankAccountStatus.ACTIVE).orElseThrow();
        Long originalId = originalLive.getId();

        expireCoolingOff(requestId);

        int handled = scheduler.runOnce();
        assertThat(handled).isEqualTo(1);

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(BankChangeStatus.ACTIVATED);
        assertThat(request.getActivatedAt()).isNotNull();

        VendorBankAccount newLive = bankAccountRepository
                .findByVendorIdAndStatus(vendor.getId(), BankAccountStatus.ACTIVE).orElseThrow();
        assertThat(newLive.getId()).isNotEqualTo(originalId);
        assertThat(newLive.getAccountReference()).isEqualTo("SYN-AC-200777");
        assertThat(newLive.getSourceRequestId()).isEqualTo(requestId);

        // The previous record survives as history rather than being overwritten.
        List<VendorBankAccount> history = bankAccountRepository.findHistoryByVendor(vendor.getId());
        assertThat(history).hasSize(2);
        VendorBankAccount superseded = bankAccountRepository.findById(originalId).orElseThrow();
        assertThat(superseded.getStatus()).isEqualTo(BankAccountStatus.SUPERSEDED);
        assertThat(superseded.getSupersededAt()).isNotNull();
    }

    @Test
    @DisplayName("a rejection at either stage stops the normal workflow")
    void rejectionStopsTheWorkflow() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");

        decide(pendingTask(requestId, ApprovalStage.FIRST), false,
                "The evidence reference does not match any document on file");

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(BankChangeStatus.REJECTED);
        assertThat(taskRefs(requestId)).hasSize(1);
    }

    @Test
    @DisplayName("the Maker can track their own requests and the cooling-off countdown")
    void makerCanTrackOwnRequests() throws Exception {
        long requestId = requestInCoolingOff();
        String token = tokenFor(maker.getUsername());

        mockMvc.perform(authed(get("/api/bank-change-requests/mine"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value((int) requestId))
                .andExpect(jsonPath("$.content[0].coolingOff.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.content[0].coolingOff.elapsed").value(false));
    }

    @Test
    @DisplayName("the whole run is recorded in the audit trail")
    void auditTrailCoversTheRun() throws Exception {
        long requestId = requestInCoolingOff();
        expireCoolingOff(requestId);
        scheduler.runOnce();

        String token = tokenFor(administrator.getUsername());
        mockMvc.perform(authed(get("/api/audit")
                        .param("entityType", "BANK_CHANGE_REQUEST")
                        .param("entityId", String.valueOf(requestId)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action=='BANK_CHANGE_REQUEST_CREATED')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='FIRST_APPROVAL_GRANTED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='SECOND_APPROVAL_GRANTED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='COOLING_OFF_STARTED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action=='BANK_DETAILS_ACTIVATED')]").isNotEmpty());
    }

    /**
     * Moves the stored deadline into the past. This is how a test
     * simulates the passage of a 24-hour window - the deadline lives in
     * the database, which is exactly why restart recovery works.
     */
    protected void expireCoolingOff(long requestId) {
        transactionTemplate.executeWithoutResult(status -> {
            BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
            ReflectionTestUtils.setField(request, "coolingOffEndAt",
                    Instant.now().minusSeconds(60));
            requestRepository.saveAndFlush(request);
        });
    }
}
