package com.vendorfraud.bankchange;

import com.vendorfraud.bankchange.dto.ApprovalTaskResponse;
import com.vendorfraud.bankchange.dto.BankChangeRequestResponse;
import com.vendorfraud.bankchange.dto.CoolingOffView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles read models. Kept separate so the write services stay about rules. */
@Service
public class BankChangeQueryService {

    private final ApprovalTaskRepository approvalTaskRepository;

    public BankChangeQueryService(ApprovalTaskRepository approvalTaskRepository) {
        this.approvalTaskRepository = approvalTaskRepository;
    }

    @Transactional(readOnly = true)
    public BankChangeRequestResponse toResponse(BankChangeRequest request) {
        List<ApprovalTaskResponse> tasks = approvalTaskRepository
                .findByBankChangeRequestIdOrderByIdAsc(request.getId()).stream()
                .map(ApprovalTaskResponse::from)
                .toList();
        return BankChangeRequestResponse.from(request, tasks, coolingOffView(request));
    }

    public CoolingOffView coolingOffView(BankChangeRequest request) {
        if (request.getCoolingOffStartAt() == null) {
            return null;
        }
        Instant now = Instant.now();
        Instant end = request.getCoolingOffEndAt();
        long remaining = end == null ? 0L : Math.max(0L, Duration.between(now, end).getSeconds());
        return new CoolingOffView(request.getCoolingOffStartAt(), end,
                request.getCoolingOffHours(), remaining,
                request.isCoolingOffElapsed(now),
                request.getCoolingOffOverrideId() != null);
    }
}
