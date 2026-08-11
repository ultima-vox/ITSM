package ru.ultimavox.itsm.platform.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class IdempotencyExceptionHandler {
  @ExceptionHandler(IdempotencyConflictException.class)
  ProblemDetail conflict(IdempotencyConflictException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Idempotency conflict");
    return problem;
  }

  @ExceptionHandler(InvalidIdempotencyKeyException.class)
  ProblemDetail invalid(InvalidIdempotencyKeyException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("Invalid Idempotency-Key");
    return problem;
  }
}

