package sandbox.java.lang;

import java.util.Objects;

/**
 * This is a dummy class. We will load the genuine class at runtime.
 */
public final class StackTraceElement extends Object implements java.io.Serializable {

    private final String declaringClass;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;

    public StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
        this.declaringClass = declaringClass;
        this.methodName     = methodName;
        this.fileName       = fileName;
        this.lineNumber     = lineNumber;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
