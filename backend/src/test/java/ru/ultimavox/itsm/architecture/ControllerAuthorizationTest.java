package ru.ultimavox.itsm.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

class ControllerAuthorizationTest {
  @Test
  void everyControllerHasExplicitAuthorizationStrategy() {
    List<String> unguarded = new ClassFileImporter()
        .importPackages("ru.ultimavox.itsm")
        .stream()
        .filter(type -> type.isAnnotatedWith(RestController.class))
        .filter(type -> !type.isAnnotatedWith(SelfScopedEndpoint.class))
        .filter(type -> !dependsOn(type, AccessControl.class.getName()))
        .filter(type -> !dependsOn(type, "ru.ultimavox.itsm.platform.ai.PolicyGate"))
        .map(JavaClass::getName)
        .sorted()
        .toList();

    assertThat(unguarded)
        .as("REST controllers must use AccessControl/PolicyGate or declare self-scoping")
        .isEmpty();
  }

  private static boolean dependsOn(JavaClass source, String targetName) {
    return source.getDirectDependenciesFromSelf().stream()
        .anyMatch(dependency -> dependency.getTargetClass().getName().equals(targetName));
  }
}
