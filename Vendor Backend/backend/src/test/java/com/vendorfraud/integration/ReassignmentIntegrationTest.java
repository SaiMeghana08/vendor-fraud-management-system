package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.ApprovalTaskStatus;
import com.vendorfraud.user.User;
import java.util.List;
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

/** Approval reassignment: who may do it, and what it may not be used to achieve. */
class ReassignmentIntegrationTest extends WorkflowScenario {

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    private String reassignBody(Long replacementId) {
        return json(Map.of("replacementUserId", replacementId,
                "reason", "The assigned approver is on leave until the end of the month"));
    }

    private User otherApprover(long excludedId) {
        return List.of(approverOne, approverTwo, approverThree).stream()
                .filter(u -> u.getId() != excludedId)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Accounts Payable may reassign a pending task to an eligible approver")
    void accountsPayableMayReassign() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);
        User replacement = otherApprover(task.assigneeId());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(replacement.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(replacement.getId().intValue()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // The old task is closed as REASSIGNED, not deleted: the trail stands.
        List<TaskRef> tasks = taskRefs(requestId);
        assertThat(tasks).hasSize(2);
        assertThat(tasks.stream().filter(t -> t.status() == ApprovalTaskStatus.REASSIGNED))
                .hasSize(1);
        assertThat(tasks.stream().filter(t -> t.status() == ApprovalTaskStatus.PENDING))
                .hasSize(1);
    }

    @Test
    @DisplayName("an Administrator may reassign; a Maker and an ordinary Approver may not")
    void reassignmentIsRoleRestricted() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);
        User replacement = otherApprover(task.assigneeId());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(maker.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(replacement.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(task.assigneeUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(replacement.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(administrator.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(replacement.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a reassignment reason is mandatory")
    void reasonIsMandatory() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);
        User replacement = otherApprover(task.assigneeId());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("replacementUserId", replacement.getId(),
                                "reason", "busy"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the Maker can never be made an approver by reassignment")
    void makerCannotBeReassignedIn() throws Exception {
        User dualRole = fixtures.createUser("maker.approver", "Dual Role", true,
                com.vendorfraud.user.RoleName.MAKER, com.vendorfraud.user.RoleName.APPROVER);
        String token = tokenFor(dualRole.getUsername());
        long requestId = longField(mockMvc.perform(
                        authed(post("/api/bank-change-requests"), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(bankChangeBody(vendor.getId()))))
                .andReturn().getResponse().getContentAsString(), "id");

        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(dualRole.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLACEMENT_IS_MAKER"));
    }

    @Test
    @DisplayName("reassignment cannot put the first approver on the second stage")
    void cannotReassignToSomeoneAlreadyInvolved() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);
        decide(first, true, "Evidence verified against the vendor letterhead");

        TaskRef second = pendingTask(requestId, ApprovalStage.SECOND);

        mockMvc.perform(authed(post("/api/approvals/tasks/" + second.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(first.assigneeId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLACEMENT_ALREADY_INVOLVED"));
    }

    @Test
    @DisplayName("an inactive or non-approver replacement is refused")
    void replacementMustBeAnActiveApprover() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);
        String token = tokenFor(accountsPayable.getUsername());

        User inactiveApprover = fixtures.createUser("approver.former", "Former Approver", false,
                com.vendorfraud.user.RoleName.APPROVER);
        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(inactiveApprover.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REPLACEMENT_INACTIVE"));

        mockMvc.perform(authed(post("/api/approvals/tasks/" + task.id() + "/reassign"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(administrator.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REPLACEMENT_NOT_APPROVER"));
    }

    @Test
    @DisplayName("a decided task cannot be reassigned")
    void decidedTaskCannotBeReassigned() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef first = pendingTask(requestId, ApprovalStage.FIRST);
        decide(first, true, "Evidence verified against the vendor letterhead");

        mockMvc.perform(authed(post("/api/approvals/tasks/" + first.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(otherApprover(first.assigneeId()).getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TASK_NOT_PENDING"));
    }

    @Test
    @DisplayName("the replacement candidate list excludes the Maker and the current assignee")
    void candidateListIsFiltered() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);

        String body = mockMvc.perform(authed(
                        get("/api/approvals/tasks/" + task.id() + "/eligible-replacements"),
                        tokenFor(accountsPayable.getUsername())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"id\":" + maker.getId())
                .doesNotContain("\"id\":" + task.assigneeId());
    }

    @Test
    @DisplayName("the reassigned approver can then decide the task")
    void reassignedApproverCanDecide() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef original = pendingTask(requestId, ApprovalStage.FIRST);
        User replacement = otherApprover(original.assigneeId());

        mockMvc.perform(authed(post("/api/approvals/tasks/" + original.id() + "/reassign"),
                        tokenFor(accountsPayable.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reassignBody(replacement.getId())))
                .andExpect(status().isOk());

        TaskRef reassigned = pendingTask(requestId, ApprovalStage.FIRST);
        assertThat(reassigned.assigneeId()).isEqualTo(replacement.getId());

        decide(reassigned, true, "Evidence verified against the vendor letterhead");

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(com.vendorfraud.bankchange.BankChangeStatus.PENDING_SECOND_APPROVAL);

        // The original approver cannot act on the old task afterwards.
        assertThat(decideStatus(original, original.assigneeUsername(), true, "Too late"))
                .isEqualTo(409);
    }
}
