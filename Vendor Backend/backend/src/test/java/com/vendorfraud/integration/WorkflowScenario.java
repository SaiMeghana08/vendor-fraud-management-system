package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.ApprovalTask;
import com.vendorfraud.bankchange.ApprovalTaskRepository;
import com.vendorfraud.bankchange.ApprovalTaskStatus;
import com.vendorfraud.bankchange.BankChangeRequestRepository;
import com.vendorfraud.support.IntegrationTestBase;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.vendor.Vendor;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared scenario helpers: drive the workflow through HTTP exactly as
 * the UI does, so every test exercises the real authorization path.
 */
abstract class WorkflowScenario extends IntegrationTestBase {

    @Autowired
    protected BankChangeRequestRepository requestRepository;

    @Autowired
    protected ApprovalTaskRepository approvalTaskRepository;

    @Autowired
    protected TransactionTemplate txTemplate;

    protected User maker;
    protected User approverOne;
    protected User approverTwo;
    protected User approverThree;
    protected User accountsPayable;
    protected User administrator;
    protected Vendor vendor;

    protected void seedOrganisation() {
        maker = fixtures.createUser("maker.one", "Maya Kerr", true, RoleName.MAKER);
        approverOne = fixtures.createUser("approver.one", "Priya Raman", true, RoleName.APPROVER);
        approverTwo = fixtures.createUser("approver.two", "Tomas Lind", true, RoleName.APPROVER);
        approverThree = fixtures.createUser("approver.three", "Grace Abara", true, RoleName.APPROVER);
        accountsPayable = fixtures.createUser("ap.one", "Ravi Chandra", true,
                RoleName.ACCOUNTS_PAYABLE);
        administrator = fixtures.createUser("admin.one", "Helen Vance", true,
                RoleName.ADMINISTRATOR);
        vendor = fixtures.createVendorWithBankAccount("VND-1001", "Northwind Industrial Supplies");
    }

    protected Map<String, Object> bankChangeBody(Long vendorId) {
        return Map.of(
                "vendorId", vendorId,
                "proposedAccountHolderName", "Northwind Industrial Supplies",
                "proposedBankName", "Fictional Mutual Bank",
                "proposedAccountReference", "SYN-AC-200777",
                "proposedRoutingCode", "SYNRT-0002",
                "reason", "Vendor consolidated its banking to a single provider",
                "evidenceReference", "EVD-2026-0001");
    }

    protected String createRequestAsMaker() throws Exception {
        String token = tokenFor(maker.getUsername());
        MvcResult result = mockMvc.perform(authed(post("/api/bank-change-requests"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bankChangeBody(vendor.getId()))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Request creation failed: HTTP "
                    + result.getResponse().getStatus() + " "
                    + result.getResponse().getContentAsString());
        }
        return result.getResponse().getContentAsString();
    }

    /**
     * Flattened view of an approval task. Tests read it inside a
     * transaction and then assert on plain values, rather than holding
     * a detached entity with lazy associations.
     */
    protected record TaskRef(long id, long assigneeId, String assigneeUsername,
                             ApprovalStage stage, ApprovalTaskStatus status) {
    }

    protected TaskRef pendingTask(Long requestId, ApprovalStage stage) {
        return taskRefs(requestId).stream()
                .filter(t -> t.stage() == stage && t.status() == ApprovalTaskStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No pending " + stage + " task for request " + requestId));
    }

    protected List<TaskRef> taskRefs(Long requestId) {
        return txTemplate.execute(status -> approvalTaskRepository
                .findByBankChangeRequestIdOrderByIdAsc(requestId).stream()
                .map(t -> new TaskRef(t.getId(), t.getAssignee().getId(),
                        t.getAssignee().getUsername(), t.getStage(), t.getStatus()))
                .toList());
    }

    protected String decide(TaskRef task, boolean approved, String comment) throws Exception {
        return decideAs(task, task.assigneeUsername(), approved, comment);
    }

    /** Lets a test decide as somebody other than the assignee. */
    protected String decideAs(TaskRef task, String username, boolean approved, String comment)
            throws Exception {
        String token = tokenFor(username);
        return mockMvc.perform(authed(
                        post("/api/approvals/tasks/" + task.id() + "/decision"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", approved, "comment", comment))))
                .andReturn().getResponse().getContentAsString();
    }

    protected int decideStatus(TaskRef task, String username, boolean approved, String comment)
            throws Exception {
        String token = tokenFor(username);
        return mockMvc.perform(authed(
                        post("/api/approvals/tasks/" + task.id() + "/decision"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", approved, "comment", comment))))
                .andReturn().getResponse().getStatus();
    }

    /** Takes a request all the way to COOLING_OFF and returns its id. */
    protected long requestInCoolingOff() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        decide(pendingTask(requestId, ApprovalStage.FIRST), true,
                "Evidence verified against the vendor letterhead");
        decide(pendingTask(requestId, ApprovalStage.SECOND), true,
                "Independently verified by a second reviewer");
        return requestId;
    }
}
