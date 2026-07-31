package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SlaServiceConditionTest {

    @Test
    void empty_condition_always_matches() {
        assertThat(SlaService.matchesCondition("", Map.of("priority", "HIGH"))).isTrue();
        assertThat(SlaService.matchesCondition(null, Map.of())).isTrue();
    }

    @Test
    void equality_condition_matches_context() {
        assertThat(SlaService.matchesCondition("priority=CRITICAL", Map.of("priority", "CRITICAL"))).isTrue();
        assertThat(SlaService.matchesCondition("priority=CRITICAL", Map.of("priority", "LOW"))).isFalse();
    }
}
