package ru.ultimavox.itsm.platform.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
public class ApiIdempotencyService {
  private static final Duration RETENTION = Duration.ofHours(24);
  private final JdbcTemplate jdbc;
  private final ObjectMapper canonicalJson;

  public ApiIdempotencyService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.canonicalJson = json.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  @Transactional
  public <T> Result<T> execute(
      String key,
      String operation,
      String actor,
      Object request,
      Class<T> responseType,
      Supplier<T> action
  ) {
    if (key == null || key.isBlank()) {
      return new Result<>(action.get(), false);
    }
    String normalizedKey = validateKey(key);
    String normalizedActor = requireText(actor, "Authenticated actor is required");
    String normalizedOperation = requireText(operation, "Idempotency operation is required");
    String org = OrganizationContext.current();
    String requestHash = sha256(write(request));
    Instant now = Instant.now();

    int inserted = jdbc.update(
        """
        INSERT INTO api_idempotency_record
          (org_id,actor_id,operation_key,idempotency_key,request_hash,created_at,expires_at)
        VALUES (?,?,?,?,?,?,?)
        ON CONFLICT DO NOTHING
        """,
        org, normalizedActor, normalizedOperation, normalizedKey, requestHash,
        Timestamp.from(now), Timestamp.from(now.plus(RETENTION))
    );
    if (inserted == 1) {
      T response = action.get();
      jdbc.update(
          """
          UPDATE api_idempotency_record
          SET response_json=?::jsonb, completed_at=?
          WHERE org_id=? AND actor_id=? AND operation_key=? AND idempotency_key=?
          """,
          write(response), Timestamp.from(Instant.now()), org, normalizedActor,
          normalizedOperation, normalizedKey
      );
      return new Result<>(response, false);
    }

    List<StoredResponse> existing = jdbc.query(
        """
        SELECT request_hash,response_json::text
        FROM api_idempotency_record
        WHERE org_id=? AND actor_id=? AND operation_key=? AND idempotency_key=?
        """,
        (rs, rowNum) -> new StoredResponse(rs.getString(1), rs.getString(2)),
        org, normalizedActor, normalizedOperation, normalizedKey
    );
    if (existing.isEmpty()) {
      throw new IdempotencyConflictException("Idempotency request is still in progress");
    }
    StoredResponse stored = existing.getFirst();
    if (!MessageDigest.isEqual(
        stored.requestHash().getBytes(StandardCharsets.US_ASCII),
        requestHash.getBytes(StandardCharsets.US_ASCII))) {
      throw new IdempotencyConflictException("Idempotency-Key was already used with a different request");
    }
    if (stored.responseJson() == null) {
      throw new IdempotencyConflictException("Idempotency request is still in progress");
    }
    return new Result<>(read(stored.responseJson(), responseType), true);
  }

  private static String validateKey(String value) {
    String key = value.trim();
    if (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
      throw new InvalidIdempotencyKeyException("Idempotency-Key must be 1-128 URL-safe characters");
    }
    return key;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value;
  }

  private String write(Object value) {
    try {
      return canonicalJson.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize idempotency payload", ex);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return canonicalJson.readValue(value, type);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot deserialize idempotency response", ex);
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public record Result<T>(T value, boolean replayed) {}
  private record StoredResponse(String requestHash, String responseJson) {}
}
