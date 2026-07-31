package ru.ultimavox.itsm.platform.sla;

import java.util.Optional;

public interface SlaPolicyRepository {
    Optional<SlaPolicy> findByKey(String policyKey);
}
