package java.util.logging;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Logger {
  private final String name;
  private static final ArrayList<Handler> handlers;
  static {
    handlers = new ArrayList<Handler>();
    handlers.add(new DefaultHandler());
  }

  public static Logger getLogger(String name) {
    return new Logger(name);
  }

  private Logger(String name) {
    this.name = name;
  }

  public List<Handler> getHandlers() {
    return new ArrayList<Handler>(handlers);
  }

  public void addHandler(Handler handler) {
    handlers.add(handler);
  }

  public void removeHandler(Handler handler) {
    handlers.remove(handler);
  }

  public void fine(String message) {
    log(Level.FINE, Method.getCaller(), message, null);
  }

  public void info(String message) {
    log(Level.INFO, Method.getCaller(), message, null);
  }

  public void warning(String message) {
    log(Level.WARNING, Method.getCaller(), message, null);
  }

  public void severe(String message) {
    log(Level.SEVERE, Method.getCaller(), message, null);
  }

  public void log(Level level, String message) {
    log(level, Method.getCaller(), message, null);
  }

  public void log(Level level, String message, Throwable exception) {
    log(level, Method.getCaller(), message, exception);
  }

  private void log(Level level, Method caller, String message,
                   Throwable exception) {
    LogRecord r = new LogRecord(name, caller.getName(), level, message,
                                exception);
    for (Handler h : handlers) {
      h.publish(r);
    }
  }

  public void setLevel(Level level) {
    // Currently ignored
  }

  private static class DefaultHandler extends Handler {
    private static final int NAME_WIDTH = 18;
    private static final int METHOD_WIDTH = 20;
    private static final int LEVEL_WIDTH = 8;

    public Object clone() { return this; }
    public void close() { }
    public void flush() { }

    private void maybeLogThrown(StringBuilder sb, Throwable t) {
      if (t != null) {
        sb.append("\nCaused by: ");
        sb.append(t.getClass().getName());
        sb.append(": ");
        sb.append(t.getMessage());
        sb.append('\n');

        for (StackTraceElement elt : t.getStackTrace()) {
          sb.append('\t');
          sb.append(elt.getClassName());
          sb.append('.');
          sb.append(elt.getMethodName());
          sb.append("(line");
          sb.append(':');
          sb.append(elt.getLineNumber());
          sb.append(')');
          sb.append('\n');
        }
        maybeLogThrown(sb, t.getCause());
      }
    }

    private void indent(StringBuilder sb, int amount) {
      do {
        sb.append(' ');
      } while (--amount > 0);
    }

    public void publish(LogRecord r) {
      StringBuilder sb = new StringBuilder();
      sb.append(r.getLoggerName());
      indent(sb, NAME_WIDTH - r.getLoggerName().length());
      sb.append(r.getSourceMethodName());
      indent(sb, METHOD_WIDTH - r.getSourceMethodName().length());
      sb.append(r.getLevel().getName());
      indent(sb, LEVEL_WIDTH - r.getLevel().getName().length());
      sb.append(r.getMessage());
      maybeLogThrown(sb, r.getThrown());
      System.out.println(sb.toString());
    }
  }

}
