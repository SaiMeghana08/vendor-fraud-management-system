package com.vendorfraud.bankchange;

import com.vendorfraud.bankchange.dto.BankChangeRequestResponse;
import com.vendorfraud.bankchange.dto.CreateBankChangeRequestCommand;
import com.vendorfraud.common.ApiPage;
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
@RequestMapping("/api/bank-change-requests")
public class BankChangeRequestController {

    private final BankChangeRequestService requestService;

    public BankChangeRequestController(BankChangeRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Creates a request. The body carries six business fields and
     * nothing else - no approver, no maker id, no reference, no status.
     */
    @PostMapping
    @PreAuthorize("hasRole('MAKER')")
    public ResponseEntity<BankChangeRequestResponse> create(
            @Valid @RequestBody CreateBankChangeRequestCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.create(command));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('MAKER')")
    public ResponseEntity<ApiPage<BankChangeRequestResponse>> myRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(requestService.listMyRequests(page, size)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<ApiPage<BankChangeRequestResponse>> list(
            @RequestParam(required = false) BankChangeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiPage.from(requestService.listAll(status, page, size)));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<BankChangeRequestResponse> get(@PathVariable Long requestId) {
        return ResponseEntity.ok(requestService.getVisibleRequest(requestId));
    }

    /** Exception monitoring for the Administrator dashboard. */
    @GetMapping("/blocked")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','ACCOUNTS_PAYABLE')")
    public ResponseEntity<List<BankChangeRequestResponse>> blocked() {
        return ResponseEntity.ok(requestService.listBlocked());
    }
}
