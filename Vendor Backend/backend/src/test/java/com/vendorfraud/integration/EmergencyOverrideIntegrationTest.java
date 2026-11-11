package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.payment.HoldStatus;
import com.vendorfraud.payment.PaymentHoldRepository;
import com.vendorfraud.scheduler.CoolingOffScheduler;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The emergency override: who may use it, what it does, and - mostly -
 * what it does not do.
 */
class EmergencyOverrideIntegrationTest extends WorkflowScenario {

    @Autowired private CoolingOffScheduler scheduler;
    @Autowired private PaymentHoldRepository holdRepository;

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    private Map<String, Object> overrideBody(long requestId) {
        return Map.of(
                "bankChangeRequestId", requestId,
                "justification", "Vendor payroll run is due today and the controller has "
                        + "confirmed the new details by callback",
                "evidenceReference", "EVD-OVR-2026-0001");
    }

    private long requestOverride(long requestId) throws Exception {
        String body = mockMvc.perform(authed(post("/api/overrides"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overrideBody(requestId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    @Test
    @DisplayName("only Accounts Payable may request an override")
    void onlyAccountsPayableMayRequest() throws Exception {
        long requestId = requestInCoolingOff();

        for (User user : java.util.List.of(maker, approverOne, administrator)) {
            mockMvc.perform(authed(post("/api/overrides"), tokenFor(user.getUsername()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(overrideBody(requestId))))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(authed(post("/api/overrides"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overrideBody(requestId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overrideReference").value(
                        org.hamcrest.Matchers.matchesPattern("EOV-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("only an Administrator may decide an override")
    void onlyAdministratorMayDecide() throws Exception {
        long requestId = requestInCoolingOff();
        long overrideId = requestOverride(requestId);

        for (User user : java.util.List.of(maker, approverOne, accountsPayable)) {
            mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                            tokenFor(user.getUsername()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("approved", true, "comment", "Approving"))))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true,
                                "comment", "Callback evidence accepted"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("the requester cannot decide their own override, even as an Administrator")
    void requesterCannotSelfApprove() throws Exception {
        User dualRole = fixtures.createUser("ap.admin", "Dual Role Finance", true,
                RoleName.ACCOUNTS_PAYABLE, RoleName.ADMINISTRATOR);
        long requestId = requestInCoolingOff();

        long overrideId = longField(mockMvc.perform(authed(post("/api/overrides"),
                                tokenFor(dualRole.getUsername()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(overrideBody(requestId))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "id");

        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                        tokenFor(dualRole.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "comment", "Deciding my own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OVERRIDE_SELF_APPROVAL"));
    }

    @Test
    @DisplayName("an override cannot be requested before dual approval is complete")
    void overrideRequiresCompletedDualApproval() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");

        mockMvc.perform(authed(post("/api/overrides"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overrideBody(requestId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUAL_APPROVAL_INCOMPLETE"));

        // Still not enough after only the first approval.
        decide(pendingTask(requestId, ApprovalStage.FIRST), true, "Evidence verified on file");
        mockMvc.perform(authed(post("/api/overrides"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overrideBody(requestId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUAL_APPROVAL_INCOMPLETE"));
    }

    @Test
    @DisplayName("an approved override brings the deadline forward but does not activate anything")
    void approvedOverrideOnlyShortensCoolingOff() throws Exception {
        long requestId = requestInCoolingOff();
        Instant originalDeadline = requestRepository.findById(requestId).orElseThrow()
                .getCoolingOffEndAt();

        long overrideId = requestOverride(requestId);
        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true,
                                "comment", "Callback evidence accepted"))))
                .andExpect(status().isOk());

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        // The deadline moved; the status did not.
        assertThat(request.getCoolingOffEndAt()).isBefore(originalDeadline);
        assertThat(request.getStatus()).isEqualTo(BankChangeStatus.COOLING_OFF);
        assertThat(request.getCoolingOffOverrideId()).isEqualTo(overrideId);

        // The scheduler then revalidates exactly as it would otherwise.
        assertThat(scheduler.runOnce()).isEqualTo(1);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATED);
    }

    @Test
    @DisplayName("an approved override never releases a payment hold")
    void approvedOverrideDoesNotReleaseHolds() throws Exception {
        long paymentId = longField(mockMvc.perform(authed(post("/api/payments"),
                                tokenFor(accountsPayable.getUsername()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("vendorId", vendor.getId(),
                                        "amount", 9000.00, "currency", "USD",
                                        "dueDate", "2026-12-20",
                                        "description", "Urgent supplier payment"))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "id");

        long requestId = requestInCoolingOff();
        assertThat(holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE))
                .hasSize(1);

        long overrideId = requestOverride(requestId);
        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "comment", "Accepted"))))
                .andExpect(status().isOk());
        scheduler.runOnce();

        // Activation happened; the hold did not move.
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.ACTIVATED);
        assertThat(holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE))
                .hasSize(1);

        // And the payment is still refused.
        mockMvc.perform(authed(post("/api/payments/" + paymentId + "/process"),
                        tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a rejected override changes nothing at all")
    void rejectedOverrideChangesNothing() throws Exception {
        long requestId = requestInCoolingOff();
        Instant originalDeadline = requestRepository.findById(requestId).orElseThrow()
                .getCoolingOffEndAt();
        long overrideId = requestOverride(requestId);

        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", false,
                                "comment", "Callback evidence is not sufficient"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        BankChangeRequest request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.getCoolingOffEndAt()).isEqualTo(originalDeadline);
        assertThat(request.getCoolingOffOverrideId()).isNull();
        assertThat(scheduler.runOnce()).isZero();
    }

    @Test
    @DisplayName("an override cannot be decided twice")
    void overrideCannotBeDecidedTwice() throws Exception {
        long requestId = requestInCoolingOff();
        long overrideId = requestOverride(requestId);
        String token = tokenFor(administrator.getUsername());
        String decision = json(Map.of("approved", true, "comment", "Accepted"));

        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId), token)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/overrides/decision/" + overrideId), token)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERRIDE_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("a second pending override for the same request is refused")
    void duplicatePendingOverrideRefused() throws Exception {
        long requestId = requestInCoolingOff();
        requestOverride(requestId);

        mockMvc.perform(authed(post("/api/overrides"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overrideBody(requestId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUPLICATE_OVERRIDE"));
    }
}
