package java.nio.channels;

import java.io.IOException;
import java.util.Iterator;

class SocketSelector extends Selector {
  private static final boolean IsWin32;
  protected long state;
  protected final Object lock = new Object();
  protected boolean woken = false;

  static {
    IsWin32 = false;
  }

  public SocketSelector() {
    state = natInit();
  }

  public boolean isOpen() {
    return state != 0;
  }

  public void wakeup() {
    synchronized (lock) {
      if (! woken) {
        woken = true;

        natWakeup(state);
      }
    }
  }

  private boolean clearWoken() {
    synchronized (lock) {
      if (woken) {
        woken = false;
        return true;
      } else {
        return false;
      }
    }
  }

  public synchronized void select(long interval) throws IOException {
    selectedKeys.clear();

    if (clearWoken()) return;
    int max=0;
    for (Iterator<SelectionKey> it = keys.iterator();
         it.hasNext();)
    {
      SelectionKey key = it.next();
      SocketChannel c = (SocketChannel)key.channel();
      int socket = c.socketFD();
      if (! c.isOpen()) {
        natSelectClearAll(socket, state);
        it.remove();
        continue;
      }

      key.readyOps(0);
      max = natSelectUpdateInterestSet(socket, key.interestOps(), state, max);
    }

    int r = natDoSocketSelect(state, max, interval);

    if (r > 0) {
      for (SelectionKey key : keys) {
        SocketChannel c = (SocketChannel)key.channel();
        int socket = c.socketFD();
        int ready = natUpdateReadySet(socket, key.interestOps(), state);
        key.readyOps(ready);
        if (ready != 0) {
          selectedKeys.add(key);
        }
      }
    }
    clearWoken();
  }

  public void close() {
    natClose(state);
  }

  private static native long natInit();
  private static native void natWakeup(long state);
  private static native void natClose(long state);
  private static native void natSelectClearAll(int socket, long state);
  private static native int natSelectUpdateInterestSet(int socket,
                                                       int interest,
                                                       long state,
                                                       int max);
  private static native int natDoSocketSelect(long state, int max, long interval)
    throws IOException;
  private static native int natUpdateReadySet(int socket, int interest, long state);
}
