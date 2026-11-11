package com.vendorfraud.unit;

import com.vendorfraud.bankchange.BankChangeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The workflow state machine, tested on its own. */
class BankChangeStatusTransitionTest {

    @Test
    @DisplayName("the only route to ACTIVATED runs through both approvals and cooling-off")
    void happyPathIsTheOnlyRouteToActivation() {
        assertThat(BankChangeStatus.PENDING_FIRST_APPROVAL
                .canTransitionTo(BankChangeStatus.PENDING_SECOND_APPROVAL)).isTrue();
        assertThat(BankChangeStatus.PENDING_SECOND_APPROVAL
                .canTransitionTo(BankChangeStatus.COOLING_OFF)).isTrue();
        assertThat(BankChangeStatus.COOLING_OFF
                .canTransitionTo(BankChangeStatus.ACTIVATED)).isTrue();
    }

    @Test
    @DisplayName("a request cannot skip the second approval")
    void cannotSkipSecondApproval() {
        assertThat(BankChangeStatus.PENDING_FIRST_APPROVAL
                .canTransitionTo(BankChangeStatus.COOLING_OFF)).isFalse();
        assertThat(BankChangeStatus.PENDING_FIRST_APPROVAL
                .canTransitionTo(BankChangeStatus.ACTIVATED)).isFalse();
    }

    @Test
    @DisplayName("a request cannot skip cooling-off")
    void cannotSkipCoolingOff() {
        assertThat(BankChangeStatus.PENDING_SECOND_APPROVAL
                .canTransitionTo(BankChangeStatus.ACTIVATED)).isFalse();
    }

    @Test
    @DisplayName("a blocked activation can still be resolved, never silently rejected")
    void blockedActivationRoutes() {
        assertThat(BankChangeStatus.ACTIVATION_BLOCKED
                .canTransitionTo(BankChangeStatus.ACTIVATED)).isTrue();
        assertThat(BankChangeStatus.ACTIVATION_BLOCKED
                .canTransitionTo(BankChangeStatus.CANCELLED)).isTrue();
        assertThat(BankChangeStatus.ACTIVATION_BLOCKED
                .canTransitionTo(BankChangeStatus.PENDING_FIRST_APPROVAL)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BankChangeStatus.class,
            names = {"ACTIVATED", "REJECTED", "CANCELLED"})
    @DisplayName("terminal states accept no further transition")
    void terminalStatesAreFinal(BankChangeStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(terminal.isOpen()).isFalse();
        assertThat(terminal.allowedNext()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = BankChangeStatus.class,
            names = {"PENDING_FIRST_APPROVAL", "PENDING_SECOND_APPROVAL", "COOLING_OFF",
                     "ACTIVATION_BLOCKED"})
    @DisplayName("non-terminal states hold the one-open-request-per-vendor slot")
    void openStatesHoldTheSlot(BankChangeStatus status) {
        assertThat(status.isOpen()).isTrue();
    }
}
