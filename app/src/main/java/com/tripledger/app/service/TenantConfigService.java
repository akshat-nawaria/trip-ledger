package com.tripledger.app.service;

import com.tripledger.app.persistence.TenantConfigRepository;
import com.tripledger.app.persistence.TenantSettings;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Per-company billing settings, e.g. "do we pay for the drive to a no-show's house?" (R04).
 *
 * A change never touches bills that were already computed. It only applies to the next computation
 * of a trip - and because the settings are part of the evidence fingerprint, the next upload of any
 * trip of this tenant is recomputed under the new settings instead of reusing the old answer.
 */
@Service
public class TenantConfigService {

    private final TenantConfigRepository repository;

    public TenantConfigService(TenantConfigRepository repository) {
        this.repository = repository;
    }

    public TenantSettings current(String tenant) {
        return repository.latest(tenant).orElse(TenantSettings.caseStudyDefaults());
    }

    public TenantSettings update(String tenant, TenantSettings requested, String changedBy) {
        try {
            requested.toRules();
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Settings rejected: " + e.getMessage());
        }
        try {
            int version = repository.insertNextVersion(tenant, requested, changedBy);
            return requested.withVersion(version);
        } catch (DuplicateKeyException e) {
            throw new ConfigConflictException("Someone else saved settings at the same moment. Reload them and try again.");
        }
    }
}
