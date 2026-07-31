package ru.ultimavox.itsm.servicedesk.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;
import ru.ultimavox.itsm.servicedesk.application.WorkItemNotFoundException;

@RestControllerAdvice(basePackages = "ru.ultimavox.itsm.servicedesk")
class ServiceDeskExceptionHandler {

  @ExceptionHandler(WorkItemNotFoundException.class)
  ProblemDetail notFound(WorkItemNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Work item not found");
    return problem;
  }

  @ExceptionHandler(IllegalStateException.class)
  ProblemDetail conflict(IllegalStateException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Illegal work item state");
    return problem;
  }

  @ExceptionHandler(WorkflowTransitionException.class)
  ProblemDetail workflowRejected(WorkflowTransitionException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Workflow transition rejected");
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("Invalid request");
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException ex) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .findFirst()
        .orElse("Validation failed");
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setTitle("Validation failed");
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden(AccessDeniedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problem.setTitle("Forbidden");
    return problem;
  }
}
