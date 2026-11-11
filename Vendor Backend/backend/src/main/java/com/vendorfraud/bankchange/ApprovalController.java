package com.vendorfraud.bankchange;

import com.vendorfraud.bankchange.dto.ApprovalDecisionCommand;
import com.vendorfraud.bankchange.dto.ApprovalTaskResponse;
import com.vendorfraud.bankchange.dto.ApproverOptionResponse;
import com.vendorfraud.bankchange.dto.BankChangeRequestResponse;
import com.vendorfraud.bankchange.dto.ReassignmentCommand;
import com.vendorfraud.common.ApiPage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ApiPage<ApprovalTaskResponse>> myTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(approvalService.listMyPendingTasks(page, size)));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ApiPage<ApprovalTaskResponse>> myHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(approvalService.listMyDecisionHistory(page, size)));
    }

    /** Pending tasks across all approvers - for reassignment screens. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApiPage<ApprovalTaskResponse>> allPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(approvalService.listAllPendingTasks(page, size)));
    }

    /**
     * Records a decision. The stage is taken from the task, the
     * approver from the token; the body carries only the decision and
     * its comment.
     */
    @PostMapping("/tasks/{taskId}/decision")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<BankChangeRequestResponse> decide(
            @PathVariable Long taskId,
            @Valid @RequestBody ApprovalDecisionCommand command) {
        return ResponseEntity.ok(approvalService.decide(taskId, command));
    }

    @GetMapping("/tasks/{taskId}/eligible-replacements")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<List<ApproverOptionResponse>> eligibleReplacements(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(approvalService.eligibleReplacements(taskId));
    }

    /** Reassignment: Accounts Payable and Administrator only. */
    @PostMapping("/tasks/{taskId}/reassign")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApprovalTaskResponse> reassign(
            @PathVariable Long taskId,
            @Valid @RequestBody ReassignmentCommand command) {
        return ResponseEntity.ok(approvalService.reassign(taskId, command));
    }
}
