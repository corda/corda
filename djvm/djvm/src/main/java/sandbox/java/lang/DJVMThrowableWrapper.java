package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

/**
 * Pinned exceptions inherit from {@link java.lang.Throwable}, but we
 * still need to be able to pass them through the sandbox's
 * exception handlers. In which case we will wrap them inside
 * one of these.
 *
 * Exceptions wrapped inside one of these cannot be caught.
 *
 * Also used for passing exceptions through finally blocks without
 * any expensive unwrapping to {@link sandbox.java.lang.Throwable}
 * based types.
 */
final class DJVMThrowableWrapper extends Throwable {
    private final java.lang.Throwable throwable;

    DJVMThrowableWrapper(java.lang.Throwable t) {
        throwable = t;
    }

    /**
     * Prevent this wrapper from creating its own stack trace.
     */
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }

    @Override
    @NotNull
    final java.lang.Throwable fromDJVM() {
        return throwable;
    }
}
