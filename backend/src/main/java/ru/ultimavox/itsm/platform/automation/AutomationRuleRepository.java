package ru.ultimavox.itsm.platform.automation;

import java.util.List;

public interface AutomationRuleRepository {
    List<AutomationRule> findEnabledByEventType(String eventType);

    /** All rules for admin list (enabled and disabled). */
    List<AutomationRule> listAll();
}
