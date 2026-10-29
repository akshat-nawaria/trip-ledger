package com.tripledger.app.security;

// the custom fields we expect inside every token (besides the standard sub / iss / exp)
public final class Claims {

    public static final String TENANT = "tenant_id";        // which client company
    public static final String ROLES = "roles";             // e.g. ["OPERATOR"]
    public static final String EMPLOYEE = "employee_ref";   // only for employees: their opaque id, never a name

    private Claims() {
    }
}
