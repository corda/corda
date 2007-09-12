package java.util;

public class EventObject {
  protected Object source;

  public EventObject(Object source) {
    this.source = source;
  }

  public Object getSource() {
    return source;
  }
}
