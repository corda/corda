package java.io;

public class FileOutputStream extends OutputStream {
//   static {
//     System.loadLibrary("natives");
//   }

  private int fd;

  public FileOutputStream(FileDescriptor fd) {
    this.fd = fd.value;
  }

  public FileOutputStream(String path) throws IOException {
    fd = open(path);
  }

  public FileOutputStream(File file) throws IOException {
    this(file.getPath());
  }

  private static native int open(String path) throws IOException;

  private static native void write(int fd, int c) throws IOException;

  private static native void write(int fd, byte[] b, int offset, int length)
    throws IOException;

  private static native void close(int fd) throws IOException;

  public void write(int c) throws IOException {
    write(fd, c);
  }

  public void write(byte[] b, int offset, int length) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }

    if (offset < 0 || offset + length > b.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    write(fd, b, offset, length);
  }

  public void close() throws IOException {
    close(fd);
    fd = -1;
  }
}
