package com.vendorfraud.override.dto;

import com.vendorfraud.override.EmergencyOverride;
import com.vendorfraud.override.OverrideStatus;
import java.time.Instant;

public record EmergencyOverrideResponse(
        Long id,
        String overrideReference,
        Long bankChangeRequestId,
        String bankChangeRequestReference,
        Long requestedBy,
        String requestedByName,
        String justification,
        String evidenceReference,
        OverrideStatus status,
        Long decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionComment,
        Instant createdAt) {

    public static EmergencyOverrideResponse from(EmergencyOverride override, String requestReference,
                                                 String requesterName, String administratorName) {
        return new EmergencyOverrideResponse(override.getId(), override.getOverrideReference(),
                override.getBankChangeRequestId(), requestReference, override.getRequestedBy(),
                requesterName, override.getJustification(), override.getEvidenceReference(),
                override.getStatus(), override.getDecidedBy(), administratorName,
                override.getDecidedAt(), override.getDecisionComment(), override.getCreatedAt());
    }
}
