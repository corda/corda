package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Throwable extends Object implements Serializable {
    private static final StackTraceElement[] NO_STACK_TRACE = new StackTraceElement[0];

    private String message;
    private Throwable cause;
    private StackTraceElement[] stackTrace;

    public Throwable() {
        this.cause = this;
        this.stackTrace = NO_STACK_TRACE;
    }

    public Throwable(String message) {
        this();
        this.message = message;
    }

    public Throwable(Throwable cause) {
        this.cause = cause;
        this.message = (cause == null) ? null : cause.toDJVMString();
        this.stackTrace = NO_STACK_TRACE;
    }

    public Throwable(String message, Throwable cause) {
        this.message = message;
        this.cause = cause;
        this.stackTrace = NO_STACK_TRACE;
    }

    public String getMessage() {
        return message;
    }

    public String getLocalizedMessage() {
        return getMessage();
    }

    public Throwable getCause() {
        return (cause == this) ? null : cause;
    }

    public Throwable initCause(Throwable cause) {
        if (this.cause != this) {
            throw new java.lang.IllegalStateException(
                    "Can't overwrite cause with " + java.util.Objects.toString(cause, "a null"), fromDJVM());
        }
        if (cause == this) {
            throw new java.lang.IllegalArgumentException("Self-causation not permitted", fromDJVM());
        }
        this.cause = cause;
        return this;
    }

    @Override
    @NotNull
    public String toDJVMString() {
        java.lang.String s = getClass().getName();
        String localized = getLocalizedMessage();
        return String.valueOf((localized != null) ? (s + ": " + localized.toString()) : s);
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace.clone();
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        StackTraceElement[] traceCopy = stackTrace.clone();

        for (int i = 0; i < traceCopy.length; ++i) {
            if (traceCopy[i] == null) {
                throw new java.lang.NullPointerException("stackTrace[" + i + "]");
            }
        }

        this.stackTrace = traceCopy;
    }

    public void printStackTrace() {}
    public Throwable fillInStackTrace() { return this; }

    @Override
    @NotNull
    java.lang.Throwable fromDJVM() {
        return DJVM.fromDJVM(this);
    }
}
