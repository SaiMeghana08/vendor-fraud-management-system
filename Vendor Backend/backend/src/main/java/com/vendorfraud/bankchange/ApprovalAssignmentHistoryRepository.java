package com.vendorfraud.bankchange;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalAssignmentHistoryRepository
        extends JpaRepository<ApprovalAssignmentHistory, Long> {

    List<ApprovalAssignmentHistory> findByApprovalTaskIdOrderByIdAsc(Long approvalTaskId);
}
