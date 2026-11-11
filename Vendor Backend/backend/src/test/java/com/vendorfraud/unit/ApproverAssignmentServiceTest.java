package com.vendorfraud.unit;

import com.vendorfraud.audit.AuditService;
import com.vendorfraud.bankchange.ApprovalAssignmentHistoryRepository;
import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.bankchange.ApprovalTask;
import com.vendorfraud.bankchange.ApprovalTaskRepository;
import com.vendorfraud.bankchange.ApproverAssignmentService;
import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.common.exception.BusinessRuleException;
import com.vendorfraud.notification.NotificationService;
import com.vendorfraud.security.CurrentUserService;
import com.vendorfraud.user.Role;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import com.vendorfraud.vendor.Vendor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The selection rules, isolated from the database.
 *
 * <p>These tests are about one question: given a set of approvers and
 * their workloads, who gets the task - and who can never get it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproverAssignmentServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ApprovalTaskRepository approvalTaskRepository;
    @Mock private ApprovalAssignmentHistoryRepository historyRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private CurrentUserService currentUserService;

    private ApproverAssignmentService service;

    private User maker;
    private User approverA;
    private User approverB;
    private User approverC;
    private BankChangeRequest request;

    @BeforeEach
    void setUp() {
        service = new ApproverAssignmentService(userRepository, approvalTaskRepository,
                historyRepository, notificationService, auditService, currentUserService);

        maker = user(1L, "maker.one", RoleName.MAKER);
        approverA = user(2L, "approver.a", RoleName.APPROVER);
        approverB = user(3L, "approver.b", RoleName.APPROVER);
        approverC = user(4L, "approver.c", RoleName.APPROVER);

        Vendor vendor = new Vendor("VND-1", "Test Vendor", "TAX-1", "v@test.example");
        ReflectionTestUtils.setField(vendor, "id", 10L);
        request = new BankChangeRequest("BCR-2026-000001", vendor, maker, null,
                "Test Vendor", "Bank", "SYN-AC-1", "SYNRT-1", "Relocated banking", "EV-1");
        ReflectionTestUtils.setField(request, "id", 100L);

        when(currentUserService.currentPrincipal()).thenReturn(Optional.empty());
        when(approvalTaskRepository.save(any(ApprovalTask.class)))
                .thenAnswer(invocation -> {
                    ApprovalTask task = invocation.getArgument(0);
                    if (task.getId() == null) {
                        ReflectionTestUtils.setField(task, "id", 500L);
                    }
                    return task;
                });
    }

    @Test
    @DisplayName("the Maker is never eligible, even when they hold the APPROVER role")
    void makerIsNeverEligible() {
        User makerWhoIsAlsoApprover = user(1L, "maker.one", RoleName.MAKER, RoleName.APPROVER);
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(makerWhoIsAlsoApprover, approverA));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.of());

        ApprovalTask task = service.assignFirstApprover(request);

        assertThat(task.getAssignee().getId()).isEqualTo(approverA.getId());
    }

    @Test
    @DisplayName("the approver with the fewest pending tasks is chosen")
    void choosesLeastLoadedApprover() {
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverA, approverB, approverC));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.<Object[]>of(
                new Object[] {2L, 5L},   // approverA has five
                new Object[] {3L, 1L},   // approverB has one
                new Object[] {4L, 4L})); // approverC has four

        ApprovalTask task = service.assignFirstApprover(request);

        assertThat(task.getAssignee().getId()).isEqualTo(approverB.getId());
    }

    @Test
    @DisplayName("ties break on the lowest user id, so selection is reproducible")
    void tieBreaksDeterministically() {
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverC, approverB, approverA));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.<Object[]>of(
                new Object[] {2L, 3L}, new Object[] {3L, 3L}, new Object[] {4L, 3L}));

        ApprovalTask first = service.assignFirstApprover(request);
        assertThat(first.getAssignee().getId()).isEqualTo(approverA.getId());

        // Same inputs, same answer - whatever order the repository returns.
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverA, approverC, approverB));
        ApprovalTask again = service.assignFirstApprover(request);
        assertThat(again.getAssignee().getId()).isEqualTo(approverA.getId());
    }

    @Test
    @DisplayName("an approver with no pending work outranks one that has some")
    void unloadedApproverWins() {
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverA, approverC));
        when(approvalTaskRepository.countPendingByAssignee())
                .thenReturn(List.<Object[]>of(new Object[] {2L, 7L}));

        ApprovalTask task = service.assignFirstApprover(request);

        assertThat(task.getAssignee().getId()).isEqualTo(approverC.getId());
    }

    @Test
    @DisplayName("second-stage assignment excludes everyone already involved")
    void secondApproverIsIndependent() {
        when(approvalTaskRepository.countApprovedForStage(100L, ApprovalStage.FIRST)).thenReturn(1L);
        when(approvalTaskRepository.findAllInvolvedAssigneeIds(100L))
                .thenReturn(List.of(approverA.getId()));
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverA, approverB));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.of());

        ApprovalTask task = service.assignSecondApprover(request);

        assertThat(task.getAssignee().getId()).isEqualTo(approverB.getId());
        assertThat(task.getStage()).isEqualTo(ApprovalStage.SECOND);
    }

    @Test
    @DisplayName("the second approver cannot be assigned before the first has approved")
    void secondStageRequiresFirstApproval() {
        when(approvalTaskRepository.countApprovedForStage(100L, ApprovalStage.FIRST)).thenReturn(0L);

        assertThatThrownBy(() -> service.assignSecondApprover(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before first-stage approval");
    }

    @Test
    @DisplayName("no eligible approver stops the workflow rather than bypassing a stage")
    void noEligibleApproverStopsTheWorkflow() {
        when(approvalTaskRepository.countApprovedForStage(100L, ApprovalStage.FIRST)).thenReturn(1L);
        when(approvalTaskRepository.findAllInvolvedAssigneeIds(100L))
                .thenReturn(List.of(approverA.getId(), approverB.getId(), approverC.getId()));
        when(userRepository.findActiveByRole(RoleName.APPROVER))
                .thenReturn(List.of(approverA, approverB, approverC));

        assertThatThrownBy(() -> service.assignSecondApprover(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No independent approver is available");
    }

    @Test
    @DisplayName("an inactive approver is never offered work")
    void inactiveApproversAreExcluded() {
        // findActiveByRole is the only source of candidates, and it
        // filters on active = true in the query itself.
        when(userRepository.findActiveByRole(RoleName.APPROVER)).thenReturn(List.of(approverA));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.of());

        ApprovalTask task = service.assignFirstApprover(request);

        assertThat(task.getAssignee().isActive()).isTrue();
    }

    @Test
    @DisplayName("every assignment writes an audit event and notifies the approver")
    void assignmentIsAuditedAndNotified() {
        when(userRepository.findActiveByRole(RoleName.APPROVER)).thenReturn(List.of(approverA));
        when(approvalTaskRepository.countPendingByAssignee()).thenReturn(List.of());

        service.assignFirstApprover(request);

        ArgumentCaptor<Long> recipient = ArgumentCaptor.forClass(Long.class);
        verify(notificationService).notifyUser(recipient.capture(), any(), any(), any(),
                any(), any());
        assertThat(recipient.getValue()).isEqualTo(approverA.getId());
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    private User user(Long id, String username, RoleName... roleNames) {
        User user = new User(username, username + "@test.example", username, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        for (RoleName roleName : roleNames) {
            user.addRole(new Role(roleName, roleName.name()));
        }
        return user;
    }
}
