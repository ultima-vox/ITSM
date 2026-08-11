package ru.ultimavox.itsm.platform.automation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRuleRepository {
    List<AutomationRule> findEnabledByEventType(String eventType);

    /** All rules for admin list (enabled and disabled). */
    List<AutomationRule> listAll();

    Optional<AutomationRule> setEnabled(UUID id, boolean enabled);
}
