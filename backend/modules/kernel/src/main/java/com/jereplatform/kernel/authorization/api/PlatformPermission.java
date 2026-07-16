package com.jereplatform.kernel.authorization.api;

import java.util.Arrays;
import java.util.Optional;

public enum PlatformPermission {
    PLATFORM_SESSION_READ("platform.session.read", "platform", false),
    PLATFORM_MEMBERSHIPS_MANAGE("platform.memberships.manage", "platform", false),
    PLATFORM_ROLES_MANAGE("platform.roles.manage", "platform", false),
    PLATFORM_ENTITLEMENTS_READ("platform.entitlements.read", "platform", false),
    ACADEMY_STUDENTS_READ("academy.students.read", "academy", true),
    ACADEMY_STUDENTS_WRITE("academy.students.write", "academy", true),
    ACADEMY_ATTENDANCE_MANAGE("academy.attendance.manage", "academy", true),
    ACADEMY_BILLING_READ("academy.billing.read", "academy", false),
    ACADEMY_BILLING_MANAGE("academy.billing.manage", "academy", false),
    COMMERCE_CUSTOMERS_READ("commerce.customers.read", "commerce", false),
    COMMERCE_INVENTORY_READ("commerce.inventory.read", "commerce", true),
    COMMERCE_INVENTORY_MANAGE("commerce.inventory.manage", "commerce", true),
    COMMERCE_CASH_READ("commerce.cash.read", "commerce", true),
    COMMERCE_CASH_MANAGE("commerce.cash.manage", "commerce", true);

    private final String code;
    private final String moduleCode;
    private final boolean branchScoped;

    PlatformPermission(String code, String moduleCode, boolean branchScoped) {
        this.code = code;
        this.moduleCode = moduleCode;
        this.branchScoped = branchScoped;
    }

    public String code() {
        return code;
    }

    public String moduleCode() {
        return moduleCode;
    }

    public boolean branchScoped() {
        return branchScoped;
    }

    public static Optional<PlatformPermission> fromCode(String code) {
        return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
    }
}
