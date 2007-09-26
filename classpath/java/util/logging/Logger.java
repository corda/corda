package java.util.logging;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Logger {
  private final String name;
  private static final ArrayList<Handler> handlers = new ArrayList<Handler>();

  public static Logger getLogger(String name) {
    return new Logger(name);
  }

  private Logger(String name) {
    this.name = name;
  }

  public List<Handler> getHandlers() {
    return handlers;
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
}
