package ru.ultimavox.itsm.platform.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuardedProbeController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("test")
class SecurityFilterChainTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;

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

}
