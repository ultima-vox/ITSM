package ru.ultimavox.itsm.platform.authorization;
/** Server-side authorization port for role, scope, ownership and field-level decisions. */
public interface PermissionChecker { Decision check(Request request); record Request(String subject, String permission, String objectType, String objectId, String field) {} record Decision(boolean allowed, String policyId) {} }
