package ru.ultimavox.itsm.platform.sla;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository {
    Optional<SlaPolicy> findByKey(String policyKey);

    /** All policies (enabled + disabled) for admin read. */
    List<SlaPolicyView> listAll();

    Optional<SlaPolicyView> update(UUID id, Boolean enabled, List<SlaPolicy.Target> targets);

    record SlaPolicyView(SlaPolicy policy, boolean enabled, int version) {}
}
