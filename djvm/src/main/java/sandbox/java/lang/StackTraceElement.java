package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

/**
 * This is a dummy class. We will load the genuine class at runtime.
 */
public final class StackTraceElement extends Object implements java.io.Serializable {

    private final String className;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;

    public StackTraceElement(String className, String methodName, String fileName, int lineNumber) {
        this.className = className;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getClassName() {
        return className;
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

    @Override
    @NotNull
    public String toDJVMString() {
        return String.toDJVM(
            className.toString() + ':' + methodName.toString()
                + (fileName != null ? '(' + fileName.toString() + ':' + lineNumber + ')' : "")
        );
    }
}
