package java.nio.channels;

import java.io.IOException;
import java.util.Iterator;

class SocketSelector extends Selector {
  private static final boolean isWin32;
  protected long state;
  protected final Object lock = new Object();
  protected boolean woken = false;

  static {
    isWin32 = false;
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
        natClearWoken(state);
        return true;
      } else {
        return false;
      }
    }
  }

  public synchronized void select(long interval) throws IOException {
    if (isWin32) {
      win32Select(interval);
    } else {
      posixSelect(interval);
    }
  }

  private void win32Select(long interval) { }

  private void posixSelect(long interval) throws IOException {
    selectedKeys.clear();

    if (clearWoken()) return;
    int max=0;
    for (Iterator<SelectionKey> it = keys.iterator();
         it.hasNext();) {
      SelectionKey key = it.next();
      SocketChannel c = (SocketChannel)key.channel();
      int socket = c.socketFD();
      if (! c.isOpen()) {
        natSelectClearAll(socket, state);
        // Equivalent to:
        //
        // FD_CLR(socket, &(s->read));
        // FD_CLR(socket, &(s->write));
        // FD_CLR(socket, &(s->except));
        it.remove();
        continue;
      }

      key.readyOps(0);
      max = natSelectUpdateInterestSet(socket, key.interestOps(), state, max);
      // Equivalent to:
      //
      // if (interest & (SelectionKey::OP_READ | SelectionKey::OP_ACCEPT)) {
      //   FD_SET(socket, &(s->read));
      //   if (max < socket) max = socket;
      // } else {
      //   FD_CLR(socket, &(s->read));
      // }
      //
      // if (interest & SelectionKey::OP_WRITE) {
      //   FD_SET(socket, &(s->write));
      //   if (max < socket) max = socket;
      // } else {
      //   FD_CLR(socket, &(s->write));
      // }
    }

    int r = natDoSocketSelect(state, max, interval);
    // Equivalent to:
    //
    // if (s->control.reader() >= 0) {
    //   unsigned socket = s->control.reader();
    //   FD_SET(socket, &(s->read));
    //   if (max < socket) max = socket;
    // }
    // timeval time = { interval / 1000, (interval % 1000) * 1000 };
    // int r = ::select(max + 1, &(s->read), &(s->write), &(s->except), &time);
    // if (r < 0) {
    //   if (errno != EINTR) {
    //     throw new IOException(errorString());
    //   }
    // }
    if (r > 0) {
      for (SelectionKey key : keys) {
        SocketChannel c = (SocketChannel)key.channel();
        int socket = c.socketFD();
        int ready = natUpdateReadySet(socket, key.interestOps(), state);
        // Equivalent to:
        //
        // jint ready = 0;
        //
        // if (FD_ISSET(c->socket, &(s->read))) {
        //   if (interest & SelectionKey::OP_READ) {
        //     ready |= SelectionKey::OP_READ;
        //   }
        //  
        //   if (interest & SelectionKey::OP_ACCEPT) {
        //     ready |= SelectionKey::OP_ACCEPT;
        //   }
        // }
        //
        // if ((interest & SelectionKey::OP_WRITE)
        //     and FD_ISSET(c->socket, &(s->write))) {
        //   ready |= SelectionKey::OP_WRITE;
        // }
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
  private static native void natClearWoken(long state);
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
