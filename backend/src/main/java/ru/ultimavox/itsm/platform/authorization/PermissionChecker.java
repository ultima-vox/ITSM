package ru.ultimavox.itsm.platform.authorization;

/**
 * Server-side authorization port for role, scope, ownership and field-level decisions.
 * Implementations must be deny-by-default.
 */
public interface PermissionChecker {

    Decision check(Request request);

    record Request(
            String subject,
            String permission,
            String objectType,
            String objectId,
            String field
    ) {}

    record Decision(boolean allowed, String policyId) {
        public static Decision deny(String policyId) {
            return new Decision(false, policyId);
        }

        public static Decision allow(String policyId) {
            return new Decision(true, policyId);
        }
    }
}
