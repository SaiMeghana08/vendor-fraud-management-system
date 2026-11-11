package com.vendorfraud.bankchange.dto;

import com.vendorfraud.user.User;

/**
 * Candidate replacement approver. Served only to Accounts Payable and
 * Administrator for the reassignment dialog - never to a Maker, and
 * never as part of request creation.
 */
public record ApproverOptionResponse(Long id, String username, String fullName) {

    public static ApproverOptionResponse from(User user) {
        return new ApproverOptionResponse(user.getId(), user.getUsername(), user.getFullName());
    }
}
