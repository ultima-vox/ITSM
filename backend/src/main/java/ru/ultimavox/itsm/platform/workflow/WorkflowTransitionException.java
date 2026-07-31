package ru.ultimavox.itsm.platform.workflow;

/** Raised when a workflow transition is not allowed. */
public class WorkflowTransitionException extends RuntimeException {

    public WorkflowTransitionException(String message) {
        super(message);
    }
}
