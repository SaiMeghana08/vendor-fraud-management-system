package com.vendorfraud.payment;

import com.vendorfraud.common.ApiPage;
import com.vendorfraud.payment.dto.CreatePaymentCommand;
import com.vendorfraud.payment.dto.PaymentEligibilityResponse;
import com.vendorfraud.payment.dto.PaymentHoldResponse;
import com.vendorfraud.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentHoldService holdService;

    public PaymentController(PaymentService paymentService, PaymentHoldService holdService) {
        this.paymentService = paymentService;
        this.holdService = holdService;
    }

    /** Five business fields only; the reference and status are generated. */
    @PostMapping
    @PreAuthorize("hasRole('ACCOUNTS_PAYABLE')")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(command));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApiPage<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(paymentService.list(status, vendorId, page, size)));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<PaymentResponse> get(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.get(paymentId));
    }

    /** Eligibility recomputed live, never read from a stored flag. */
    @GetMapping("/{paymentId}/eligibility")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<PaymentEligibilityResponse> eligibility(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.checkEligibility(paymentId));
    }

    @PostMapping("/{paymentId}/process")
    @PreAuthorize("hasRole('ACCOUNTS_PAYABLE')")
    public ResponseEntity<PaymentResponse> process(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.process(paymentId));
    }

    @GetMapping("/{paymentId}/holds")
    @PreAuthorize("hasAnyRole('ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<List<PaymentHoldResponse>> holds(@PathVariable Long paymentId) {
        return ResponseEntity.ok(holdService.listHoldsForPayment(paymentId));
    }
}
