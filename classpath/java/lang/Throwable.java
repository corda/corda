package java.lang;

public class Throwable {
  private String message;
  private Object trace;
  private Throwable cause;

  public Throwable(String message, Throwable cause) {
    this.message = message;
    this.trace = trace(1);
    this.cause = cause;
  }

  public Throwable(String message) {
    this(message, null);
  }

  public Throwable(Throwable cause) {
    this(null, cause);
  }

  public Throwable() {
    this(null, null);
  }

  private static native Object trace(int skipCount);

  private static native StackTraceElement[] resolveTrace(Object trace);

  private StackTraceElement[] resolveTrace() {
    if (! (trace instanceof StackTraceElement[])) {
      trace = resolveTrace(trace);
    }
    return (StackTraceElement[]) trace;
  }

  public void printStackTrace() {
    StringBuilder sb = new StringBuilder();
    printStackTrace(sb, System.getProperty("line.separator"));
    System.err.print(sb.toString());
  }

  private void printStackTrace(StringBuilder sb, String nl) {
    sb.append(getClass().getName());
    if (message != null) {
      sb.append(": ").append(message);
    }
    sb.append(nl);

    StackTraceElement[] trace = resolveTrace();
    for (int i = 0; i < trace.length; ++i) {
      sb.append("  at ")
        .append(trace[i].getClassName())
        .append(".")
        .append(trace[i].getMethodName());

      if (trace[i].isNativeMethod()) {
        sb.append(" (native)");
      } else {
        int line = trace[i].getLineNumber();
        if (line >= 0) {
          sb.append(" (line ").append(line).append(")");
        }
      }

      sb.append(nl);
    }

    if (cause != null) {
      sb.append("caused by: ");
      cause.printStackTrace(sb, nl);
    }
  }
}
