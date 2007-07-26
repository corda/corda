package java.io;

public class FileInputStream extends InputStream {
  private final int fd;

  public FileInputStream(FileDescriptor fd) {
    this.fd = fd.value;
  }

  private static native int read(int fd) throws IOException;

  private static native int read(int fd, byte[] b, int offset, int length)
    throws IOException;

  public static native void close(int fd) throws IOException;

  public int read() throws IOException {
    return read(fd);
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    return read(fd, b, offset, length);
  }

  public void close() throws IOException {
    close(fd);
  }
}
