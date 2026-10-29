package com.tripledger.app.security;

import java.util.Optional;
import java.util.Set;

// who is making the current request, straight from their (already verified) token
public record Caller(String tenant, String subject, Set<Role> roles, String employeeRef) {

    public Caller {
        roles = Set.copyOf(roles);
    }

    /**
     * Plain employees may only see trips they rode in. This returns their employee ref when that
     * restriction applies, or empty when the caller is an operator / admin and sees all trips.
     * An employee token without an employee_ref gets "" - which matches no trip at all.
     */
    public Optional<String> onlyTripsOf() {
        boolean staff = roles.contains(Role.OPERATOR) || roles.contains(Role.ADMIN);
        if (staff) {
            return Optional.empty();
        }
        return Optional.of(employeeRef == null ? "" : employeeRef);
    }
}
