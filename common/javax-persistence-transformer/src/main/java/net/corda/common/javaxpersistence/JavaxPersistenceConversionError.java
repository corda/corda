package net.corda.common.javaxpersistence;

/**
 * Thrown when a class built for Corda 4.14 or earlier cannot be converted for Hibernate 7. A {@link LinkageError} rather than an exception,
 * so that it is not swallowed by the code that loads CorDapp classes defensively and the node fails to start with the message instead.
 */
public final class JavaxPersistenceConversionError extends LinkageError {
    public JavaxPersistenceConversionError(String message) {
        super(message);
    }
}
