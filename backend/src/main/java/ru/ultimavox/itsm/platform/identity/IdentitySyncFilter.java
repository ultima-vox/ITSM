package ru.ultimavox.itsm.platform.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public final class IdentitySyncFilter extends OncePerRequestFilter {
  private final IdentitySyncService identitySync;

  public IdentitySyncFilter(IdentitySyncService identitySync) {
    this.identitySync = identitySync;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/actuator/health");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth
        && jwtAuth.isAuthenticated()
        && jwtAuth.getToken() != null) {
      try {
        identitySync.sync(jwtAuth.getToken());
      } catch (DisabledException | InvalidBearerTokenException ex) {
        throw ex;
      } catch (RuntimeException ex) {
        throw new AuthenticationServiceException("Identity synchronization failed", ex);
      }
    }
    chain.doFilter(request, response);
  }
}
