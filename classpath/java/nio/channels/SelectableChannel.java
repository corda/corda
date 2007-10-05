package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class SelectableChannel implements Channel {
  private SelectionKey key;

  public abstract int read(ByteBuffer b) throws Exception;
  public abstract int write(ByteBuffer b) throws Exception;
  public abstract boolean isOpen();

  public SelectionKey register(Selector selector, int interestOps,
                               Object attachment)
  {
    SelectionKey key = new SelectionKey
      (this, selector, interestOps, attachment);
    selector.add(key);
    return key;
  }

  public void close() throws IOException {
    if (key != null) {
      key.selector().remove(key);
      key = null;
    }
  }
}
