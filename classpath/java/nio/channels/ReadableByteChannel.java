package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ReadableByteChannel extends Channel {
  public int read(ByteBuffer b) throws IOException;
}
