package java.io;

public class FileInputStream extends InputStream {
  private int fd;

  public FileInputStream(FileDescriptor fd) {
    this.fd = fd.value;
  }

  public FileInputStream(String path) throws IOException {
    fd = open(path);
  }

  public FileInputStream(File file) throws IOException {
    this(file.getPath());
  }

  private static native int open(String path) throws IOException;

  private static native int read(int fd) throws IOException;

  private static native int read(int fd, byte[] b, int offset, int length)
    throws IOException;

  public static native void close(int fd) throws IOException;

  public int read() throws IOException {
    return read(fd);
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }

    if (offset < 0 || offset + length > b.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    return read(fd, b, offset, length);
  }

  public void close() throws IOException {
    close(fd);
    fd = -1;
  }
}
