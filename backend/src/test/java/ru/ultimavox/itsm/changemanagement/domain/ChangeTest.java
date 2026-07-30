package ru.ultimavox.itsm.changemanagement.domain;
import static org.assertj.core.api.Assertions.*; import java.time.*; import java.util.UUID; import org.junit.jupiter.api.Test;
class ChangeTest { private Change draft(){return new Change(UUID.randomUUID(),"CHG-1001",Change.Type.NORMAL,Change.Risk.HIGH,Change.Status.DRAFT,Instant.parse("2026-08-10T20:00:00Z"),Instant.parse("2026-08-10T21:00:00Z"),"Deploy blue-green","Return traffic to blue");}
 @Test void normal_change_cannot_skip_risk_assessment(){assertThatThrownBy(()->draft().transition(Change.Status.SCHEDULED,true)).isInstanceOf(IllegalStateException.class);}
 @Test void scheduling_requires_approval(){var authorized=draft().transition(Change.Status.ASSESSMENT,false).transition(Change.Status.AUTHORIZATION,false); assertThatThrownBy(()->authorized.transition(Change.Status.SCHEDULED,false)).hasMessageContaining("approved"); assertThat(authorized.transition(Change.Status.SCHEDULED,true).status()).isEqualTo(Change.Status.SCHEDULED);}
}
