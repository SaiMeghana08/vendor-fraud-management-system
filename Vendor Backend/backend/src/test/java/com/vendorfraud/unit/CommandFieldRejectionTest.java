package com.vendorfraud.unit;

import com.vendorfraud.bankchange.dto.CreateBankChangeRequestCommand;
import com.vendorfraud.common.exception.UnexpectedFieldException;
import com.vendorfraud.payment.dto.CreatePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deserialisation-level proof that a client cannot steer the workflow
 * by adding a field. These run without Spring: the rejection is a
 * property of the command objects themselves.
 */
class CommandFieldRejectionTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("a Maker cannot name the first approver")
    void rejectsFirstApproverId() {
        String body = """
                {"vendorId":1,"proposedAccountHolderName":"Acme Ltd","proposedBankName":"Bank",
                 "proposedAccountReference":"SYN-AC-9","proposedRoutingCode":"SYNRT-1",
                 "reason":"Vendor relocated banking","evidenceReference":"EV-1",
                 "firstApproverId":7}
                """;
        assertThatThrownBy(() -> mapper.readValue(body, CreateBankChangeRequestCommand.class))
                .hasRootCauseInstanceOf(UnexpectedFieldException.class);
    }

    @Test
    @DisplayName("a Maker cannot name themselves, the reference or the status")
    void rejectsOtherServerControlledFields() {
        for (String field : new String[] {"makerId", "requestReference", "status",
                                          "approvalStage", "secondApproverId"}) {
            String body = """
                    {"vendorId":1,"proposedAccountHolderName":"Acme Ltd","proposedBankName":"Bank",
                     "proposedAccountReference":"SYN-AC-9","proposedRoutingCode":"SYNRT-1",
                     "reason":"Vendor relocated banking","evidenceReference":"EV-1",
                     "%s":"x"}
                    """.formatted(field);
            assertThatThrownBy(() -> mapper.readValue(body, CreateBankChangeRequestCommand.class))
                    .as("field %s must be rejected", field)
                    .hasRootCauseInstanceOf(UnexpectedFieldException.class);
        }
    }

    @Test
    @DisplayName("a client cannot supply a payment reference")
    void rejectsClientSuppliedPaymentReference() {
        String body = """
                {"vendorId":1,"amount":100.00,"currency":"USD","dueDate":"2026-12-01",
                 "description":"Quarterly services","paymentReference":"PAY-2026-000999"}
                """;
        assertThatThrownBy(() -> mapper.readValue(body, CreatePaymentCommand.class))
                .hasRootCauseInstanceOf(UnexpectedFieldException.class);
    }

    @Test
    @DisplayName("the six permitted Maker fields deserialise normally")
    void acceptsOnlyTheBusinessFields() {
        String body = """
                {"vendorId":42,"proposedAccountHolderName":"Acme Ltd","proposedBankName":"Bank",
                 "proposedAccountReference":"SYN-AC-9","proposedRoutingCode":"SYNRT-1",
                 "reason":"Vendor relocated banking","evidenceReference":"EV-1"}
                """;
        CreateBankChangeRequestCommand command =
                mapper.readValue(body, CreateBankChangeRequestCommand.class);
        assertThat(command.getVendorId()).isEqualTo(42L);
        assertThat(command.getEvidenceReference()).isEqualTo("EV-1");
    }
}
