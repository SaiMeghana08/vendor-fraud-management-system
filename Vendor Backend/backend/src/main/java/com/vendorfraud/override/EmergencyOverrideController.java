package com.vendorfraud.override;

import com.vendorfraud.common.ApiPage;
import com.vendorfraud.override.dto.EmergencyOverrideResponse;
import com.vendorfraud.override.dto.OverrideDecisionCommand;
import com.vendorfraud.override.dto.RequestOverrideCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/overrides")
public class EmergencyOverrideController {

    private final EmergencyOverrideService overrideService;

    public EmergencyOverrideController(EmergencyOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    /** Requesting is Accounts Payable's; deciding is not. */
    @PostMapping
    @PreAuthorize("hasRole('ACCOUNTS_PAYABLE')")
    public ResponseEntity<EmergencyOverrideResponse> request(
            @Valid @RequestBody RequestOverrideCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(overrideService.request(command));
    }

    /** Deciding is the Administrator's alone. */
    @PostMapping("/decision/{overrideId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<EmergencyOverrideResponse> decide(
            @PathVariable Long overrideId,
            @Valid @RequestBody OverrideDecisionCommand command) {
        return ResponseEntity.ok(overrideService.decide(overrideId, command));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApiPage<EmergencyOverrideResponse>> list(
            @RequestParam(required = false) OverrideStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(overrideService.list(status, page, size)));
    }

    @GetMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<EmergencyOverrideResponse> get(@PathVariable Long overrideId) {
        return ResponseEntity.ok(overrideService.get(overrideId));
    }
}
