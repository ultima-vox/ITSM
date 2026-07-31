package ru.ultimavox.itsm.platform.metadata;

import java.util.List;

/** Raised when an instance payload fails object-definition schema rules. */
public class ObjectValidationException extends RuntimeException {

    private final List<String> errors;

    public ObjectValidationException(List<String> errors) {
        super("Object payload validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
