package ru.ultimavox.itsm.platform.idempotency;

public final class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String message) {
    super(message);
  }
}

