package java.io;

public class FileInputStream extends InputStream {
  private final FileDescriptor fd;

  public FileInputStream(FileDescriptor fd) {
    this.fd = fd;
  }

  public native int read() throws IOException;

  public native int read(byte[] b, int offset, int length) throws IOException;

  public native void close() throws IOException;
}
