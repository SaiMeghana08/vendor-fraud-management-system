package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.vendor.Vendor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The controls, stated as the things the system refuses to do. Each
 * test here corresponds to a way the workflow could be subverted.
 */
class NegativeControlsIntegrationTest extends WorkflowScenario {

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    // ---------------------------------------------------------------- //
    // Maker cannot influence approver selection
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("a Maker naming the first approver is rejected, not quietly ignored")
    void makerCannotSelectApprover() throws Exception {
        Map<String, Object> body = new HashMap<>(bankChangeBody(vendor.getId()));
        body.put("firstApproverId", approverOne.getId());

        mockMvc.perform(authed(post("/api/bank-change-requests"), tokenFor(maker.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNEXPECTED_FIELD"));

        // Nothing was created by the rejected call.
        assertThat(requestRepository.findOpenByVendor(vendor.getId())).isEmpty();
    }

    @Test
    @DisplayName("a Maker cannot supply a maker id, a reference or a status")
    void makerCannotSupplyServerControlledFields() throws Exception {
        for (Map.Entry<String, Object> forbidden : Map.<String, Object>of(
                "makerId", 999,
                "requestReference", "BCR-2026-999999",
                "status", "ACTIVATED",
                "secondApproverId", 3).entrySet()) {
            Map<String, Object> body = new HashMap<>(bankChangeBody(vendor.getId()));
            body.put(forbidden.getKey(), forbidden.getValue());

            mockMvc.perform(authed(post("/api/bank-change-requests"),
                            tokenFor(maker.getUsername()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------- //
    // Separation of duties
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("a Maker who also holds APPROVER cannot approve their own request")
    void makerCannotSelfApprove() throws Exception {
        User dualRole = fixtures.createUser("maker.approver", "Dual Role", true,
                RoleName.MAKER, RoleName.APPROVER);
        String token = tokenFor(dualRole.getUsername());

        long requestId = longField(mockMvc.perform(
                        authed(post("/api/bank-change-requests"), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(bankChangeBody(vendor.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);
        // The assignment itself already excluded them...
        assertThat(first.assigneeId()).isNotEqualTo(dualRole.getId());

        // ...and a direct attempt is refused too.
        mockMvc.perform(authed(post("/api/approvals/tasks/" + first.id() + "/decision"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "comment", "Approving my own"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CANNOT_APPROVE"));
    }

    @Test
    @DisplayName("an approver who is not the assignee cannot decide the task")
    void onlyAssignedApproverMayDecide() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        String otherApprover = java.util.List.of(
                        approverOne, approverTwo, approverThree).stream()
                .filter(u -> !u.getId().equals(first.assigneeId()))
                .findFirst().orElseThrow().getUsername();

        mockMvc.perform(authed(post("/api/approvals/tasks/" + first.id() + "/decision"),
                        tokenFor(otherApprover))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "comment", "Not mine to decide"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_APPROVER"));
    }

    @Test
    @DisplayName("the first approver is never assigned the second stage")
    void sameApproverCannotApproveBothStages() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);
        decide(first, true, "Evidence verified against the vendor letterhead");

        TaskRef second = pendingTask(requestId, ApprovalStage.SECOND);
        assertThat(second.assigneeId()).isNotEqualTo(first.assigneeId());

        // And the first approver cannot reach into the second stage.
        assertThat(decideStatus(second, first.assigneeUsername(), true, "Approving again"))
                .isEqualTo(403);
    }

    @Test
    @DisplayName("a decided task cannot be decided again")
    void duplicateDecisionIsRejected() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        decide(first, true, "Evidence verified against the vendor letterhead");

        assertThat(decideStatus(first, first.assigneeUsername(), true, "Approving twice"))
                .isEqualTo(409);
    }

    @Test
    @DisplayName("a second-stage decision cannot be made before the first stage exists")
    void outOfOrderApprovalIsRejected() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");

        // There is no second-stage task to decide yet - the stages are
        // created in order, so the out-of-order call has nothing to hit.
        assertThat(taskRefs(requestId)).hasSize(1);
        assertThat(taskRefs(requestId).get(0).stage()).isEqualTo(ApprovalStage.FIRST);

        // Deciding a non-existent task is a 404, never a shortcut.
        mockMvc.perform(authed(post("/api/approvals/tasks/999999/decision"),
                        tokenFor(approverOne.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "comment", "Jumping the queue"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a rejection without a meaningful comment is refused")
    void rejectionRequiresComment() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        assertThat(decideStatus(first, first.assigneeUsername(), false, "no")).isEqualTo(422);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(BankChangeStatus.PENDING_FIRST_APPROVAL);
    }

    // ---------------------------------------------------------------- //
    // Role boundaries
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("a Maker cannot reach the payment, hold, override or audit endpoints")
    void makerIsLockedOutOfOtherModules() throws Exception {
        String token = tokenFor(maker.getUsername());

        mockMvc.perform(authed(get("/api/payments"), token)).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/holds"), token)).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/audit"), token)).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/api/overrides"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankChangeRequestId", 1,
                                "justification", "Trying to request an override as a Maker",
                                "evidenceReference", "EVD-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only an Administrator may read the audit trail")
    void auditIsAdministratorOnly() throws Exception {
        mockMvc.perform(authed(get("/api/audit"), tokenFor(approverOne.getUsername())))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/audit"), tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/audit"), tokenFor(administrator.getUsername())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("there is no endpoint that edits or deletes an audit record")
    void auditHasNoWriteEndpoints() throws Exception {
        String token = tokenFor(administrator.getUsername());
        mockMvc.perform(authed(post("/api/audit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/audit/1"), token))
                .andExpect(status().is4xxClientError());
    }

    // ---------------------------------------------------------------- //
    // Vendors and duplicates
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("a soft-deleted vendor is invisible and cannot be selected")
    void softDeletedVendorCannotBeUsed() throws Exception {
        Vendor retired = fixtures.createVendor("VND-9001", "Obsolete Trading Co", false);
        String token = tokenFor(maker.getUsername());

        mockMvc.perform(authed(get("/api/vendors"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.vendorCode=='VND-9001')]").isEmpty());

        mockMvc.perform(authed(post("/api/bank-change-requests"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bankChangeBody(retired.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a second open request for the same vendor is refused")
    void duplicateOpenRequestIsRefused() throws Exception {
        createRequestAsMaker();

        mockMvc.perform(authed(post("/api/bank-change-requests"), tokenFor(maker.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bankChangeBody(vendor.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUPLICATE_OPEN_REQUEST"));
    }

    @Test
    @DisplayName("proposing the details the vendor already has is refused")
    void noChangeProposedIsRefused() throws Exception {
        Vendor other = fixtures.createVendorWithBankAccount("VND-2002", "Helios Facility Services");

        Map<String, Object> body = new HashMap<>(bankChangeBody(other.getId()));
        body.put("proposedAccountReference", fixtures.activeAccountReference(other.getId()));
        body.put("proposedRoutingCode", "SYNRT-0001");

        mockMvc.perform(authed(post("/api/bank-change-requests"), tokenFor(maker.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_CHANGE_PROPOSED"));
    }

    // ---------------------------------------------------------------- //
    // Notification privacy
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("one user cannot read or mark another user's notification")
    void notificationsArePrivate() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);

        // The assigned approver has a notification; find its id.
        String assigneeToken = tokenFor(first.assigneeUsername());
        String list = mockMvc.perform(authed(get("/api/notifications"), assigneeToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long notificationId = objectMapper.readTree(list).get("content").get(0).get("id").asLong();

        // Another approver cannot see it in their own list...
        String otherUsername = java.util.List.of(approverOne, approverTwo, approverThree).stream()
                .filter(u -> !u.getId().equals(first.assigneeId()))
                .findFirst().orElseThrow().getUsername();
        mockMvc.perform(authed(get("/api/notifications"), tokenFor(otherUsername)))
                .andExpect(jsonPath("$.content[?(@.id==" + notificationId + ")]").isEmpty());

        // ...and cannot mark it read either.
        mockMvc.perform(authed(post("/api/notifications/" + notificationId + "/read"),
                        tokenFor(otherUsername)))
                .andExpect(status().isNotFound());

        // The owner can.
        mockMvc.perform(authed(post("/api/notifications/" + notificationId + "/read"),
                        assigneeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

}
