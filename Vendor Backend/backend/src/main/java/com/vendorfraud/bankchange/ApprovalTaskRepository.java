package com.vendorfraud.bankchange;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    List<ApprovalTask> findByBankChangeRequestIdOrderByIdAsc(Long requestId);

    Optional<ApprovalTask> findByBankChangeRequestIdAndStageAndStatus(
            Long requestId, ApprovalStage stage, ApprovalTaskStatus status);

    Page<ApprovalTask> findByAssigneeIdAndStatusOrderByIdDesc(
            Long assigneeId, ApprovalTaskStatus status, Pageable pageable);

    Page<ApprovalTask> findByAssigneeIdOrderByIdDesc(Long assigneeId, Pageable pageable);

    Page<ApprovalTask> findByStatusOrderByIdAsc(ApprovalTaskStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApprovalTask t where t.id = :id")
    Optional<ApprovalTask> findByIdForUpdate(@Param("id") Long id);

    /** Workload per approver, feeding least-pending-task selection. */
    @Query("""
           select t.assignee.id, count(t) from ApprovalTask t
            where t.status = com.vendorfraud.bankchange.ApprovalTaskStatus.PENDING
            group by t.assignee.id
           """)
    List<Object[]> countPendingByAssignee();

    /**
     * Everyone who has ever held or decided a stage of this request.
     * Used to keep the second approver independent of the first - even
     * if the first task was later reassigned away from them.
     */
    @Query("""
           select distinct t.assignee.id from ApprovalTask t
            where t.bankChangeRequest.id = :requestId
           """)
    List<Long> findAllInvolvedAssigneeIds(@Param("requestId") Long requestId);

    @Query("""
           select count(t) from ApprovalTask t
            where t.bankChangeRequest.id = :requestId
              and t.stage = :stage
              and t.status = com.vendorfraud.bankchange.ApprovalTaskStatus.APPROVED
           """)
    long countApprovedForStage(@Param("requestId") Long requestId, @Param("stage") ApprovalStage stage);
}
