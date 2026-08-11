package ru.ultimavox.itsm.platform.idempotency;

public final class InvalidIdempotencyKeyException extends RuntimeException {
  public InvalidIdempotencyKeyException(String message) {
    super(message);
  }
}

