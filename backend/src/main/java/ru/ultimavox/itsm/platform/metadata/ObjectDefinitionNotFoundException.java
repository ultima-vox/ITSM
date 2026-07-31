package ru.ultimavox.itsm.platform.metadata;

/** Raised when no active object definition exists for the requested key. */
public class ObjectDefinitionNotFoundException extends RuntimeException {

    public ObjectDefinitionNotFoundException(String objectKey) {
        super("No active object definition for key: " + objectKey);
    }
}
