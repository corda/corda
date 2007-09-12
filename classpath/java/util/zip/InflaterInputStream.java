package java.util.zip;

import java.io.InputStream;
import java.io.IOException;

public class InflaterInputStream extends InputStream {
  private final InputStream in;

  public InflaterInputStream(InputStream in) {
    this.in = in;
  }

  public int read() throws IOException {
    throw new IOException("not implemented");
  }

  public void close() throws IOException {
    in.close();
  }
}
