package java.io;

public class FileOutputStream extends OutputStream {
  private final int fd;

  public FileOutputStream(FileDescriptor fd) {
    this.fd = fd.value;
  }

  public static native void write(int fd, int c) throws IOException;

  public static native void write(int fd, byte[] b, int offset, int length)
    throws IOException;

  public static native void close(int fd) throws IOException;

  public void write(int c) throws IOException {
    write(fd, c);
  }

  public void write(byte[] b, int offset, int length) throws IOException {
    write(fd, b, offset, length);
  }

  public void close() throws IOException {
    close(fd);
  }
}
