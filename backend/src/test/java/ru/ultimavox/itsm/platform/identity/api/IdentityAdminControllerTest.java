package ru.ultimavox.itsm.platform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.GroupRoleMappingRecord;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.IdentityAccountRecord;

class IdentityAdminControllerTest {
  @Test
  void listAccountsRequiresRbacRead() {
    IdentityQueryService query = Mockito.mock(IdentityQueryService.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    when(auth.getName()).thenReturn("admin");
    UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    Instant synced = Instant.parse("2026-08-22T12:00:00Z");
    when(query.listAccounts(0, 200)).thenReturn(List.of(
        new IdentityAccountRecord(
            id,
            "http://localhost/realms/itsm",
            "ext-1",
            "sub-1",
            true,
            synced,
            List.of("REQUESTER")
        )
    ));

    var response = new IdentityAdminController(query, access).listAccounts(auth, 0, 200);

    verify(access).require("admin", "rbac.read", "identity_account", null);
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().idp()).isEqualTo("http://localhost/realms/itsm");
    assertThat(response.getFirst().externalId()).isEqualTo("ext-1");
    assertThat(response.getFirst().subjectId()).isEqualTo("sub-1");
    assertThat(response.getFirst().enabled()).isTrue();
    assertThat(response.getFirst().lastSync()).isEqualTo(synced);
    assertThat(response.getFirst().roleKeys()).containsExactly("REQUESTER");
  }

  @Test
  void listGroupMappingsRequiresRbacRead() {
    IdentityQueryService query = Mockito.mock(IdentityQueryService.class);
    AccessControl access = Mockito.mock(AccessControl.class);
    Authentication auth = Mockito.mock(Authentication.class);
    when(auth.getName()).thenReturn("admin");
    when(query.listGroupMappings(0, 200)).thenReturn(List.of(
        new GroupRoleMappingRecord("ITSM-Users", "REQUESTER")
    ));

    var response = new IdentityAdminController(query, access).listGroupMappings(auth, 0, 200);

    verify(access).require("admin", "rbac.read", "group_role_mapping", null);
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().idpGroup()).isEqualTo("ITSM-Users");
    assertThat(response.getFirst().roleName()).isEqualTo("REQUESTER");
  }
}
