package ru.ultimavox.itsm.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.GuardedEndpoint;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

/** Fail-closed safety net: every API controller must declare or inject an authorization boundary. */
@Component
class ApiAuthorizationBoundaryInterceptor implements HandlerInterceptor {
  private final Map<Class<?>, Boolean> guarded = new ConcurrentHashMap<>();

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!request.getRequestURI().startsWith("/api/") || !(handler instanceof HandlerMethod method)) {
      return true;
    }
    Class<?> controller = method.getBeanType();
    if (!guarded.computeIfAbsent(controller, this::hasBoundary)) {
      throw new AccessDeniedException(
          "API controller has no authorization boundary: " + controller.getName());
    }
    return true;
  }

  boolean hasBoundary(Class<?> controller) {
    if (AnnotatedElementUtils.hasAnnotation(controller, SelfScopedEndpoint.class)
        || AnnotatedElementUtils.hasAnnotation(controller, GuardedEndpoint.class)) {
      return true;
    }
    for (Class<?> type = controller; type != null && type != Object.class; type = type.getSuperclass()) {
      if (Arrays.stream(type.getDeclaredFields()).map(Field::getType)
          .anyMatch(AccessControl.class::isAssignableFrom)) {
        return true;
      }
    }
    return false;
  }
}
