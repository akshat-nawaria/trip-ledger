package com.tripledger.app.api;

import com.tripledger.app.api.dto.BillingDtos.AdjustmentOut;
import com.tripledger.app.api.dto.BillingDtos.AdjustmentsResponse;
import com.tripledger.app.api.dto.BillingDtos.BillRequest;
import com.tripledger.app.api.dto.BillingDtos.BillingResponse;
import com.tripledger.app.api.dto.BillingDtos;
import com.tripledger.app.security.CurrentCaller;
import com.tripledger.app.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// billing (admin) and the adjustment trail (operator + admin) - see SecurityConfig
@RestController
@RequestMapping("/v1/trips")
@Tag(name = "Billing", description = "Put a trip on an invoice; see the signed corrections that came after")
public class BillingController {

    private final BillingService billing;
    private final CurrentCaller caller;

    public BillingController(BillingService billing, CurrentCaller caller) {
        this.billing = billing;
        this.caller = caller;
    }

    @PostMapping("/{tripId}/bill")
    @Operation(summary = "Put the trip's current km on an invoice",
            description = "Freezes the current run and km. Later changes never edit this invoice; they become "
                    + "signed adjustments. Billing the same invoice again is a no-op; another invoice is 409.")
    public BillingResponse bill(@PathVariable String tripId, @Valid @RequestBody BillRequest request) {
        return BillingResponse.of(tripId, billing.bill(caller.get().tenant(), tripId, request.invoiceId()));
    }

    @GetMapping("/{tripId}/adjustments")
    @Operation(summary = "Signed km corrections since the trip was billed (e.g. late pings, recovery after an outage)")
    public AdjustmentsResponse adjustments(@PathVariable String tripId) {
        BillingService.AdjustmentsView view = billing.adjustments(caller.get().tenant(), tripId);
        return new AdjustmentsResponse(tripId, view.billing().invoiceId(), BillingDtos.km(view.billing().billedKm()),
                BillingDtos.km(view.chargedKm()), view.adjustments().stream().map(AdjustmentOut::of).toList());
    }
}
