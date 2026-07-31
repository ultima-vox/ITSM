package ru.ultimavox.itsm.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects a synthetic authenticated principal for local demos under profile {@code dev}.
 * Grants admin-style authorities so AccessControl / RBAC paths remain exercisable.
 */
final class DevAuthenticationFilter extends OncePerRequestFilter {

  static final String DEV_SUBJECT = "dev-local";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null
        || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
      var authentication = new UsernamePasswordAuthenticationToken(
          DEV_SUBJECT,
          "N/A",
          List.of(
              new SimpleGrantedAuthority("ROLE_itsm_admin"),
              new SimpleGrantedAuthority("ROLE_ADMIN"),
              new SimpleGrantedAuthority("ROLE_itsm_admin_full")
          )
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
