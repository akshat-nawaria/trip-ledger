package com.tripledger.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

// who is limited to "own trips only" - the rule behind the employee 404s
class CallerTest {

    @Test
    void aPlainEmployeeOnlySeesTheirOwnTrips() {
        Caller employee = new Caller("acme", "u1", Set.of(Role.EMPLOYEE), "EMP-2417");

        assertThat(employee.onlyTripsOf()).isEqualTo(Optional.of("EMP-2417"));
    }

    @Test
    void operatorsAndAdminsSeeEveryTripOfTheirCompany() {
        assertThat(new Caller("acme", "u2", Set.of(Role.OPERATOR), null).onlyTripsOf()).isEqualTo(Optional.empty());
        assertThat(new Caller("acme", "u3", Set.of(Role.ADMIN), null).onlyTripsOf()).isEqualTo(Optional.empty());
    }

    @Test
    void anEmployeeWhoIsAlsoStaffIsNotLimited() {
        Caller both = new Caller("acme", "u4", Set.of(Role.EMPLOYEE, Role.OPERATOR), "EMP-2417");

        assertThat(both.onlyTripsOf()).isEqualTo(Optional.empty());
    }

    @Test
    void anEmployeeTokenWithoutAnEmployeeRefMatchesNoTripAtAll() {
        Caller nobody = new Caller("acme", "u5", Set.of(Role.EMPLOYEE), null);

        assertThat(nobody.onlyTripsOf()).isEqualTo(Optional.of(""));
    }

    @Test
    void knownRolesAreRecognisedAndOthersAreNot() {
        assertThat(Role.isKnown("ADMIN")).isTrue();
        assertThat(Role.isKnown("admin")).isFalse();
        assertThat(Role.isKnown("SUPERUSER")).isFalse();
    }
}
