package ru.ultimavox.itsm.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityKeycloakRealmImportTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void demoRealmKeepsPasswordGrantUsers() throws IOException {
    JsonNode realm = readJson(repoFile("infra/keycloak/itsm-realm.json"));
    assertThat(realm.path("realm").asText()).isEqualTo("itsm");
    assertThat(realm.path("bruteForceProtected").asBoolean()).isTrue();

    List<String> usernames = new ArrayList<>();
    for (JsonNode user : realm.path("users")) {
      usernames.add(user.path("username").asText());
    }
    assertThat(usernames).contains("anna", "admin", "requester");

    JsonNode spa = client(realm, "itsm-spa");
    JsonNode backend = client(realm, "itsm-backend");
    assertThat(spa.path("directAccessGrantsEnabled").asBoolean()).isTrue();
    assertThat(backend.path("directAccessGrantsEnabled").asBoolean()).isTrue();
    assertThat(backend.path("secret").asText()).isEqualTo("itsm-backend-secret");
  }

  @Test
  void prodRealmHasNoDemoUsersOrPasswordGrantOrHardcodedSecret() throws IOException {
    Path prodPath = repoFile("infra/keycloak/itsm-realm-prod.json");
    String raw = Files.readString(prodPath);
    JsonNode realm = MAPPER.readTree(raw);

    assertThat(realm.path("realm").asText()).isEqualTo("itsm");
    assertThat(realm.path("bruteForceProtected").asBoolean()).isTrue();
    assertThat(realm.path("sslRequired").asText()).isEqualTo("all");
    assertThat(realm.path("users")).isEmpty();
    assertThat(raw).doesNotContain("itsm-backend-secret");
    assertThat(raw).doesNotContain("\"username\": \"anna\"");
    assertThat(raw).doesNotContain("\"username\": \"admin\"");
    assertThat(raw).doesNotContain("\"username\": \"requester\"");

    JsonNode spa = client(realm, "itsm-spa");
    JsonNode backend = client(realm, "itsm-backend");
    assertThat(spa.path("directAccessGrantsEnabled").asBoolean()).isFalse();
    assertThat(backend.path("directAccessGrantsEnabled").asBoolean()).isFalse();
    assertThat(spa.path("standardFlowEnabled").asBoolean()).isTrue();
    assertThat(spa.path("publicClient").asBoolean()).isTrue();
    assertThat(backend.has("secret")).isFalse();
    assertThat(backend.path("publicClient").asBoolean()).isFalse();

    List<String> groups = new ArrayList<>();
    for (JsonNode group : realm.path("groups")) {
      groups.add(group.path("name").asText());
    }
    assertThat(groups).containsExactlyInAnyOrder(
        "ITSM-Users",
        "ITSM-ServiceDesk",
        "ITSM-ServiceDesk-Managers",
        "ITSM-Change-Managers",
        "ITSM-CAB",
        "ITSM-Admins"
    );

    boolean totpEnabled = false;
    for (JsonNode action : realm.path("requiredActions")) {
      if ("CONFIGURE_TOTP".equals(action.path("alias").asText())) {
        totpEnabled = action.path("enabled").asBoolean();
      }
    }
    assertThat(totpEnabled).isTrue();
  }

  @Test
  void composeSelectsDemoVsProdRealmAndProdRequiresAdminPassword() throws IOException {
    String demo = Files.readString(repoFile("docker-compose.yml"));
    String prod = Files.readString(repoFile("docker-compose.prod.yml"));

    assertThat(demo).contains("itsm-realm.json:/opt/keycloak/data/import/itsm-realm.json");
    assertThat(demo).doesNotContain("itsm-realm-prod.json");
    assertThat(demo).contains("start-dev");
    assertThat(demo).contains("${KC_ADMIN_PASSWORD:-admin}");

    assertThat(prod).contains("itsm-realm-prod.json:/opt/keycloak/data/import/itsm-realm.json");
    assertThat(prod).doesNotContain("./infra/keycloak:/opt/keycloak/data/import");
    assertThat(prod).contains("\"start\"");
    assertThat(prod).doesNotContain("start-dev");
    assertThat(prod).contains("${KC_ADMIN_PASSWORD:?");
    assertThat(prod).doesNotContain("${KC_ADMIN_PASSWORD:-admin}");
  }

  private static JsonNode client(JsonNode realm, String clientId) {
    for (JsonNode client : realm.path("clients")) {
      if (clientId.equals(client.path("clientId").asText())) {
        return client;
      }
    }
    throw new AssertionError("missing client " + clientId);
  }

  private static JsonNode readJson(Path path) throws IOException {
    return MAPPER.readTree(Files.readString(path));
  }

  private static Path repoFile(String relative) {
    Path cwd = Path.of("").toAbsolutePath();
    Path direct = cwd.resolve(relative);
    if (Files.isRegularFile(direct)) {
      return direct;
    }
    Path sibling = cwd.resolve("..").resolve(relative).normalize();
    if (Files.isRegularFile(sibling)) {
      return sibling;
    }
    throw new IllegalStateException("missing " + relative + " from " + cwd);
  }
}
