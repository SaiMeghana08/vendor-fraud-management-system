package com.vendorfraud.user;

/**
 * The four - and only four - business roles. Authorization in this
 * application is role-based; there is no permission table, no
 * permission entity and no permission check anywhere.
 */
public enum RoleName {
    MAKER,
    APPROVER,
    ACCOUNTS_PAYABLE,
    ADMINISTRATOR;

    /** Spring Security authority form, e.g. {@code ROLE_MAKER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
