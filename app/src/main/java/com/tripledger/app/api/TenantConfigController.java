package com.tripledger.app.api;

import com.tripledger.app.api.dto.TenantConfigDto;
import com.tripledger.app.security.Caller;
import com.tripledger.app.security.CurrentCaller;
import com.tripledger.app.service.TenantConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// admin only (see SecurityConfig). The tenant is always the caller's own - there's no way to name another.
@RestController
@RequestMapping("/v1/tenant/config")
@Tag(name = "Tenant settings", description = "Billing settings of your company (admin only)")
public class TenantConfigController {

    private final TenantConfigService settings;
    private final CurrentCaller caller;

    public TenantConfigController(TenantConfigService settings, CurrentCaller caller) {
        this.settings = settings;
        this.caller = caller;
    }

    @GetMapping
    @Operation(summary = "Current billing settings (version 0 = case-study defaults)")
    public TenantConfigDto current() {
        return TenantConfigDto.from(settings.current(caller.get().tenant()));
    }

    @PutMapping
    @Operation(summary = "Save new billing settings as the next version",
            description = "Bills already computed are not touched. Trips are computed with the new settings from their next upload on.")
    public TenantConfigDto update(@Valid @RequestBody TenantConfigDto body) {
        Caller me = caller.get();
        return TenantConfigDto.from(settings.update(me.tenant(), body.toSettings(), me.subject()));
    }
}
