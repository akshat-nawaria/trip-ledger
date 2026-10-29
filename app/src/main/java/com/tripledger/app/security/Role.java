package com.tripledger.app.security;

/**
 * Who can do what (also in DESIGN.md, section 9):
 *   EMPLOYEE - read the km of their own trips, nothing else
 *   OPERATOR - upload evidence, read any trip and its breakdown (the vendor / fleet side)
 *   ADMIN    - everything inside their own company, incl. settings and the trip list
 */
public enum Role {
    EMPLOYEE,
    OPERATOR,
    ADMIN;

    public static boolean isKnown(String name) {
        for (Role role : values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
