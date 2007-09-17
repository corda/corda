package java.util.zip;

import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;

public class InflaterInputStream extends InputStream {
  private final InputStream in;
  private final Inflater inflater;
  private final byte[] buffer;

  public InflaterInputStream(InputStream in, Inflater inflater, int bufferSize)
  {
    this.in = in;
    this.inflater = inflater;
    this.buffer = new byte[bufferSize];
  }

  public InflaterInputStream(InputStream in, Inflater inflater) {
    this(in, inflater, 4 * 1024);
  }

  public InflaterInputStream(InputStream in) {
    this(in, new Inflater());
  }

  public int read() throws IOException {
    byte[] buffer = new byte[1];
    int c = read(buffer);
    return (c < 0 ? c : (buffer[0] & 0xFF));
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    if (inflater.finished()) {
      return -1;
    }

    while (true) {
      if (inflater.needsInput()) {
        int count = in.read(buffer);
        if (count > 0) {
          inflater.setInput(buffer, 0, count);
        } else {
          throw new EOFException();
        }
      }

      try {
        int count = inflater.inflate(b, offset, length);
        if (count > 0) {
          return count;
        } else if (inflater.needsDictionary()) {
          throw new IOException("missing dictionary");
        }
      } catch (DataFormatException e) {
        throw new IOException(e);
      }
    }
  }

  public void close() throws IOException {
    in.close();
    inflater.dispose();
  }
}
