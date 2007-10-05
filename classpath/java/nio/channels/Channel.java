package java.nio.channels;

import java.io.IOException;

public interface Channel {
  public void close() throws IOException;
  public boolean isOpen();
}
