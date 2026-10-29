package com.tripledger.app.api.dto;

import com.tripledger.app.persistence.TenantSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of GET / PUT /v1/tenant/config. On PUT the version is ignored (the server assigns the next one).
 * Ranges are checked by the billing rules themselves, so the rules for "what's a valid value" live in
 * exactly one place.
 */
@Schema(description = "Billing settings for your company")
public record TenantConfigDto(
        @Schema(description = "settings version; 0 = case-study defaults, never changed", accessMode = Schema.AccessMode.READ_ONLY)
        Integer version,
        @NotNull @Schema(description = "pay for the drive to a no-show's pickup point", example = "false")
        Boolean noShowAnchorEnabled,
        @NotNull @Schema(description = "drop pings less accurate than this", example = "50")
        Integer maxAccuracyMetres,
        @NotNull @Schema(description = "anything faster is treated as a GPS glitch", example = "120")
        Double maxSpeedKmph,
        @NotNull @Schema(description = "trust the real trip end only this close to the office", example = "400")
        Integer officeRadiusMetres,
        @NotNull @Schema(description = "a routed piece longer than this is rejected", example = "100")
        Integer maxRouteKm) {

    public static TenantConfigDto from(TenantSettings s) {
        return new TenantConfigDto(s.version(), s.noShowAnchorEnabled(), s.maxAccuracyMetres(), s.maxSpeedKmph(),
                s.officeRadiusMetres(), s.maxRouteKm());
    }

    public TenantSettings toSettings() {
        return new TenantSettings(0, noShowAnchorEnabled, maxAccuracyMetres, maxSpeedKmph, officeRadiusMetres, maxRouteKm);
    }
}
