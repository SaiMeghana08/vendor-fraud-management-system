package com.vendorfraud.payment;

import com.vendorfraud.common.ApiPage;
import com.vendorfraud.payment.dto.PaymentHoldResponse;
import com.vendorfraud.payment.dto.ReleaseHoldCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hold release is its own endpoint, separate from every bank-change and
 * override operation. That separation is the point: nothing else in the
 * system can reach it.
 */
@RestController
@RequestMapping("/api/holds")
public class PaymentHoldController {

    private final PaymentHoldService holdService;

    public PaymentHoldController(PaymentHoldService holdService) {
        this.holdService = holdService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApiPage<PaymentHoldResponse>> listActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(holdService.listActiveHolds(page, size)));
    }

    @PostMapping("/{holdId}/release")
    @PreAuthorize("hasRole('ACCOUNTS_PAYABLE')")
    public ResponseEntity<PaymentHoldResponse> release(
            @PathVariable Long holdId,
            @Valid @RequestBody ReleaseHoldCommand command) {
        return ResponseEntity.ok(holdService.releaseHold(holdId, command.justification()));
    }
}
