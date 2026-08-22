package ru.ultimavox.itsm.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class IdentitySyncFilterTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void syncsAuthenticatedJwtThenContinues() throws Exception {
    IdentitySyncService identitySync = mock(IdentitySyncService.class);
    IdentitySyncFilter filter = new IdentitySyncFilter(identitySync);
    Jwt jwt = jwt("user-1");
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/work-items");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(identitySync).sync(jwt);
    verify(chain).doFilter(request, response);
  }

  @Test
  void disabledAccountDoesNotContinue() throws Exception {
    IdentitySyncService identitySync = mock(IdentitySyncService.class);
    IdentitySyncFilter filter = new IdentitySyncFilter(identitySync);
    Jwt jwt = jwt("disabled-user");
    doThrow(new DisabledException("Identity account is disabled")).when(identitySync).sync(jwt);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/work-items");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(DisabledException.class);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void skipsHealth() throws Exception {
    IdentitySyncService identitySync = mock(IdentitySyncService.class);
    IdentitySyncFilter filter = new IdentitySyncFilter(identitySync);
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.setRequestURI("/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(identitySync, never()).sync(org.mockito.ArgumentMatchers.any());
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static Jwt jwt(String subject) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuer("http://localhost/realms/itsm")
        .subject(subject)
        .audience(List.of("itsm-backend"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }
}
