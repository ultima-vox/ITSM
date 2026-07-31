package ru.ultimavox.itsm.platform.authorization;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deny-by-default composite: any participating checker may allow.
 * Order does not matter for allow; first allow wins for policy attribution.
 */
@Component
@Primary
class CompositePermissionChecker implements PermissionChecker {

    private final List<AuthorityPermissionChecker> checkers;

    CompositePermissionChecker(List<AuthorityPermissionChecker> checkers) {
        this.checkers = List.copyOf(checkers);
    }

    @Override
    public Decision check(Request request) {
        for (AuthorityPermissionChecker checker : checkers) {
            Decision decision = checker.check(request);
            if (decision.allowed()) {
                return decision;
            }
        }
        return Decision.deny("composite-default-deny");
    }
}
