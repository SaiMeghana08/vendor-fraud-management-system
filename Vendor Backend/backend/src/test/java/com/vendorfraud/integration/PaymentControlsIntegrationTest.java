package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.payment.HoldStatus;
import com.vendorfraud.payment.PaymentHoldRepository;
import com.vendorfraud.payment.PaymentRepository;
import com.vendorfraud.payment.PaymentStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payment creation, holds, eligibility and release. */
class PaymentControlsIntegrationTest extends WorkflowScenario {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentHoldRepository holdRepository;

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    private Map<String, Object> paymentBody(Long vendorId) {
        return Map.of(
                "vendorId", vendorId,
                "amount", 12500.00,
                "currency", "USD",
                "dueDate", "2026-12-15",
                "description", "Quarterly facility services");
    }

    private long createPayment() throws Exception {
        String body = mockMvc.perform(authed(post("/api/payments"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(paymentBody(vendor.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longField(body, "id");
    }

    @Test
    @DisplayName("the backend generates the payment reference and the initial status")
    void backendGeneratesPaymentReference() throws Exception {
        String body = mockMvc.perform(authed(post("/api/payments"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(paymentBody(vendor.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.eligibleForProcessing").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(stringField(body, "paymentReference")).matches("PAY-\\d{4}-\\d{6}");
        // Masked on the way out, like every other banking reference.
        assertThat(stringField(body, "maskedPayToAccountReference")).contains("•");
    }

    @Test
    @DisplayName("consecutive payments get distinct, sequential references")
    void referencesAreSequentialAndUnique() throws Exception {
        long first = createPayment();
        long second = createPayment();

        String a = paymentRepository.findById(first).orElseThrow().getPaymentReference();
        String b = paymentRepository.findById(second).orElseThrow().getPaymentReference();

        assertThat(a).isNotEqualTo(b);
        assertThat(Integer.parseInt(b.substring(b.lastIndexOf('-') + 1)))
                .isEqualTo(Integer.parseInt(a.substring(a.lastIndexOf('-') + 1)) + 1);
    }

    @Test
    @DisplayName("a client-supplied payment reference is rejected")
    void clientSuppliedReferenceRejected() throws Exception {
        Map<String, Object> body = new HashMap<>(paymentBody(vendor.getId()));
        body.put("paymentReference", "PAY-2026-000999");

        mockMvc.perform(authed(post("/api/payments"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNEXPECTED_FIELD"));
    }

    @Test
    @DisplayName("raising a bank-change request holds the vendor's existing payments")
    void bankChangeRequestHoldsExistingPayments() throws Exception {
        long paymentId = createPayment();

        createRequestAsMaker();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.ON_HOLD);
        assertThat(holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("a payment raised while a bank change is open is held immediately")
    void newPaymentDuringOpenRequestIsHeld() throws Exception {
        createRequestAsMaker();

        long paymentId = createPayment();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.ON_HOLD);
    }

    @Test
    @DisplayName("a held payment cannot be processed")
    void heldPaymentCannotBeProcessed() throws Exception {
        long paymentId = createPayment();
        createRequestAsMaker();

        mockMvc.perform(authed(post("/api/payments/" + paymentId + "/process"),
                        tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ELIGIBLE"));

        mockMvc.perform(authed(get("/api/payments/" + paymentId + "/eligibility"),
                        tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.activeHoldCount").value(1));
    }

    @Test
    @DisplayName("activation does not release holds on its own")
    void activationDoesNotReleaseHolds() throws Exception {
        long paymentId = createPayment();
        long requestId = requestInCoolingOff();

        // Force the window closed and let the scheduler activate.
        txTemplate.executeWithoutResult(s -> {
            var request = requestRepository.findById(requestId).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(request,
                    "coolingOffEndAt", java.time.Instant.now().minusSeconds(60));
            requestRepository.saveAndFlush(request);
        });
        schedulerRunOnce();

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(com.vendorfraud.bankchange.BankChangeStatus.ACTIVATED);
        // The hold survives activation: resuming payment is a separate decision.
        assertThat(holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE))
                .hasSize(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.ON_HOLD);
    }

    @Test
    @DisplayName("a rejection does not release holds either")
    void rejectionDoesNotReleaseHolds() throws Exception {
        long paymentId = createPayment();
        long requestId = longField(createRequestAsMaker(), "id");

        decide(pendingTask(requestId, ApprovalStage.FIRST), false,
                "Evidence does not match any document on file");

        assertThat(holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("only Accounts Payable releases a hold, and only with a justification")
    void holdReleaseIsRestrictedAndJustified() throws Exception {
        long paymentId = createPayment();
        createRequestAsMaker();
        long holdId = holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE)
                .get(0).getId();

        // An Administrator may see holds but may not release one.
        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("justification",
                                "Administrator attempting a release"))))
                .andExpect(status().isForbidden());

        // A blank or trivial justification is refused.
        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("justification", "ok"))))
                .andExpect(status().isBadRequest());

        // A justified release by Accounts Payable succeeds...
        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("justification",
                                "Vendor confirmed banking details by callback to a known number"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedBy").value(accountsPayable.getId().intValue()));

        // ...and the payment returns to a payable state.
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    @DisplayName("releasing a hold twice is refused")
    void doubleReleaseIsRefused() throws Exception {
        long paymentId = createPayment();
        createRequestAsMaker();
        long holdId = holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE)
                .get(0).getId();
        String token = tokenFor(accountsPayable.getUsername());
        String justification = json(Map.of("justification",
                "Vendor confirmed details by callback to a known number"));

        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(justification))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(justification))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_ALREADY_RELEASED"));
    }

    @Test
    @DisplayName("a released hold still leaves the payment blocked while the request is open")
    void openRequestKeepsPaymentIneligible() throws Exception {
        long paymentId = createPayment();
        createRequestAsMaker();
        long holdId = holdRepository.findByPaymentIdAndStatus(paymentId, HoldStatus.ACTIVE)
                .get(0).getId();

        mockMvc.perform(authed(post("/api/holds/" + holdId + "/release"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("justification",
                                "Urgent supplier payment agreed with the controller"))))
                .andExpect(status().isOk());

        // Eligibility is recomputed from live state, and the vendor
        // still has a bank change in flight.
        mockMvc.perform(authed(get("/api/payments/" + paymentId + "/eligibility"),
                        tokenFor(accountsPayable.getUsername())))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reason").value("The vendor has an open bank-change request"));
    }

    @Test
    @DisplayName("an unencumbered payment can be processed once, and not twice")
    void processingIsIdempotentlyGuarded() throws Exception {
        long paymentId = createPayment();
        String token = tokenFor(accountsPayable.getUsername());

        mockMvc.perform(authed(post("/api/payments/" + paymentId + "/process"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(authed(post("/api/payments/" + paymentId + "/process"), token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a payment for a vendor with no banking record is refused")
    void vendorWithoutBankAccountCannotBePaid() throws Exception {
        var bare = fixtures.createVendor("VND-3003", "Vendor Without Banking", true);

        mockMvc.perform(authed(post("/api/payments"), tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(paymentBody(bare.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VENDOR_HAS_NO_ACTIVE_BANK_ACCOUNT"));
    }

    @Test
    @DisplayName("holds list is visible to Accounts Payable and the Administrator only")
    void holdListVisibility() throws Exception {
        createPayment();
        createRequestAsMaker();

        mockMvc.perform(authed(get("/api/holds"), tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
        mockMvc.perform(authed(get("/api/holds"), tokenFor(administrator.getUsername())))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/holds"), tokenFor(approverOne.getUsername())))
                .andExpect(status().isForbidden());
    }

    private void schedulerRunOnce() {
        scheduler.runOnce();
    }

    @Autowired
    private com.vendorfraud.scheduler.CoolingOffScheduler scheduler;
}
