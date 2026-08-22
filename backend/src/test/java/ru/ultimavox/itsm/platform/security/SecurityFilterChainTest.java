package ru.ultimavox.itsm.platform.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.ultimavox.itsm.platform.identity.IdentitySyncService;

@WebMvcTest(GuardedProbeController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("test")
class SecurityFilterChainTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean IdentitySyncService identitySync;

  @Test
  void protectedEndpointRejectsMissingJwt() throws Exception {
    mvc.perform(get("/api/v1/security-probe"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedJwtReachesProtectedEndpoint() throws Exception {
    mvc.perform(get("/api/v1/security-probe").with(jwt().jwt(token -> token.subject("operator-1"))))
        .andExpect(status().isOk());
  }

  @Test
  void disabledIdentityAccountCannotAuthenticate() throws Exception {
    doThrow(new DisabledException("Identity account is disabled")).when(identitySync).sync(any());
    mvc.perform(get("/api/v1/security-probe").with(jwt().jwt(token -> token.subject("disabled-user"))))
        .andExpect(status().isUnauthorized());
  }

}
