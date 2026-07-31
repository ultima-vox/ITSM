package ru.ultimavox.itsm.platform.sla;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository {
    Optional<SlaPolicy> findByKey(String policyKey);

    /** All policies (enabled + disabled) for admin read. */
    List<SlaPolicyView> listAll();

    record SlaPolicyView(SlaPolicy policy, boolean enabled, int version) {}
}
