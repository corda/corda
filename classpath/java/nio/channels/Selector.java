package java.nio.channels;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

public abstract class Selector {
  protected final Set<SelectionKey> keys = new HashSet();
  protected final Set<SelectionKey> selectedKeys = new HashSet();

  public static Selector open() {
    return new SocketSelector();
  }
  
  public void add(SelectionKey key) {
    keys.add(key);
  }

  public void remove(SelectionKey key) {
    keys.remove(key);
  }

  public Set<SelectionKey> keys() {
    return keys;
  }

  public Set<SelectionKey> selectedKeys() {
    return selectedKeys;
  }

  public abstract boolean isOpen();

  public abstract void wakeup();

  public abstract void select(long interval) throws IOException;

  public abstract void close();
}
