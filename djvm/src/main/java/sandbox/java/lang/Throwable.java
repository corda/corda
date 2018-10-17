package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;
import sandbox.TaskTypes;

import java.io.Serializable;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Throwable extends Object implements Serializable {
    private static final StackTraceElement[] NO_STACK_TRACE = new StackTraceElement[0];

    private String message;
    private Throwable cause;
    private StackTraceElement[] stackTrace;

    public Throwable() {
        this.cause = this;
        fillInStackTrace();
    }

    public Throwable(String message) {
        this();
        this.message = message;
    }

    public Throwable(Throwable cause) {
        this.cause = cause;
        this.message = (cause == null) ? null : cause.toDJVMString();
        fillInStackTrace();
    }

    public Throwable(String message, Throwable cause) {
        this.message = message;
        this.cause = cause;
        fillInStackTrace();
    }

    protected Throwable(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        if (writableStackTrace) {
            fillInStackTrace();
        } else {
            stackTrace = NO_STACK_TRACE;
        }
        this.message = message;
        this.cause = cause;
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
        return (stackTrace == NO_STACK_TRACE) ? stackTrace : stackTrace.clone();
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        StackTraceElement[] traceCopy = stackTrace.clone();

        for (int i = 0; i < traceCopy.length; ++i) {
            if (traceCopy[i] == null) {
                throw new java.lang.NullPointerException("stackTrace[" + i + ']');
            }
        }

        this.stackTrace = traceCopy;
    }

    @SuppressWarnings({"ThrowableNotThrown", "UnusedReturnValue"})
    public Throwable fillInStackTrace() {
        if (stackTrace == null) {
            /*
             * We have been invoked from within this exception's constructor.
             * Work our way up the stack trace until we find this constructor,
             * and then find out who actually invoked it. This is where our
             * sandboxed stack trace will start from.
             *
             * Our stack trace will end at the point where we entered the sandbox.
             */
            final java.lang.StackTraceElement[] elements = new java.lang.Throwable().getStackTrace();
            final java.lang.String exceptionName = getClass().getName();
            int startIdx = 1;
            while (startIdx < elements.length && !isConstructorFor(elements[startIdx], exceptionName)) {
                ++startIdx;
            }
            while (startIdx < elements.length && isConstructorFor(elements[startIdx], exceptionName)) {
                ++startIdx;
            }

            int endIdx = startIdx;
            while (endIdx < elements.length && !TaskTypes.isEntryPoint(elements[endIdx])) {
                ++endIdx;
            }
            stackTrace = (startIdx == elements.length) ? NO_STACK_TRACE : DJVM.copyToDJVM(elements, startIdx, endIdx);
        }
        return this;
    }

    private static boolean isConstructorFor(java.lang.StackTraceElement elt, java.lang.String className) {
        return elt.getClassName().equals(className) && elt.getMethodName().equals("<init>");
    }

    public void printStackTrace() {}

    @Override
    @NotNull
    java.lang.Throwable fromDJVM() {
        return DJVM.fromDJVM(this);
    }
}
