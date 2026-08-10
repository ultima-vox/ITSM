package ru.ultimavox.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleSqlOwnershipTest {
  private static final Path SOURCES = Path.of("src/main/java/ru/ultimavox/itsm");

  @Test
  void businessModulesDoNotQueryForeignOwnedTables() throws IOException {
    List<Rule> rules = List.of(
        new Rule("problemmanagement", "work_item"),
        new Rule("assetmanagement", "configuration_item"),
        new Rule("servicedesk", "configuration_item"),
        new Rule("servicedesk", "attachment"),
        new Rule("servicecatalog", "form_definition"),
        new Rule("servicecatalog", "workflow_definition")
    );

    for (Rule rule : rules) {
      Pattern foreignSql = Pattern.compile(
          "(?is)\\b(from|join|update|into|delete\\s+from)\\s+" + Pattern.quote(rule.table()) + "\\b"
      );
      Path module = SOURCES.resolve(rule.module());
      try (var files = Files.walk(module)) {
        List<Path> violations = files
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> {
              try {
                return foreignSql.matcher(Files.readString(path)).find();
              } catch (IOException ex) {
                throw new IllegalStateException(ex);
              }
            })
            .toList();
        assertThat(violations)
            .as("%s must access %s only through a public module contract", rule.module(), rule.table())
            .isEmpty();
      }
    }
  }

  private record Rule(String module, String table) {}
}
