package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.GuardedEndpoint;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

class ApiAuthorizationBoundaryInterceptorTest {
  private final ApiAuthorizationBoundaryInterceptor interceptor =
      new ApiAuthorizationBoundaryInterceptor();

  @Test
  void rejectsUnguardedApiBeforeHandlerExecution() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/unguarded");
    var handler = new HandlerMethod(new Unguarded(), Unguarded.class.getDeclaredMethod("endpoint"));
    assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
        .isInstanceOf(AccessDeniedException.class).hasMessageContaining(Unguarded.class.getName());
  }

  @Test
  void acceptsDirectRbacSelfScopeAndDedicatedPolicyGate() {
    assertThat(interceptor.hasBoundary(DirectRbac.class)).isTrue();
    assertThat(interceptor.hasBoundary(SelfScope.class)).isTrue();
    assertThat(interceptor.hasBoundary(DedicatedGate.class)).isTrue();
  }

  @Test
  void everyCurrentRestControllerHasExplicitBoundary() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    List<String> unguarded = new ArrayList<>();
    for (var bean : scanner.findCandidateComponents("ru.ultimavox.itsm")) {
      Class<?> controller = Class.forName(bean.getBeanClassName());
      if (!interceptor.hasBoundary(controller)) unguarded.add(controller.getName());
    }
    assertThat(unguarded).isEmpty();
  }

  static class Unguarded { public void endpoint() {} }
  static class DirectRbac { @SuppressWarnings("unused") private AccessControl access; }
  @SelfScopedEndpoint static class SelfScope {}
  @GuardedEndpoint static class DedicatedGate {}
}
