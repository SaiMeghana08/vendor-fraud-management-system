package com.vendorfraud.bankchange.dto;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.ApprovalTask;
import com.vendorfraud.bankchange.ApprovalTaskStatus;
import java.time.Instant;

public record ApprovalTaskResponse(
        Long id,
        Long requestId,
        String requestReference,
        ApprovalStage stage,
        Long assigneeId,
        String assigneeName,
        ApprovalTaskStatus status,
        String decisionComment,
        Instant assignedAt,
        Instant decidedAt) {

    public static ApprovalTaskResponse from(ApprovalTask task) {
        return new ApprovalTaskResponse(task.getId(),
                task.getBankChangeRequest().getId(),
                task.getBankChangeRequest().getRequestReference(),
                task.getStage(),
                task.getAssignee().getId(),
                task.getAssignee().getFullName(),
                task.getStatus(),
                task.getDecisionComment(),
                task.getAssignedAt(),
                task.getDecidedAt());
    }
}
