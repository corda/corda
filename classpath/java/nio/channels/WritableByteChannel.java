package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface WritableByteChannel extends Channel {
  public int write(ByteBuffer b) throws IOException;
}
