package java.io;

public class FileOutputStream extends OutputStream {
  private final FileDescriptor fd;

  public FileOutputStream(FileDescriptor fd) {
    this.fd = fd;
  }

  public native void write(int c) throws IOException;

  public native void write(byte[] b, int offset, int length)
    throws IOException;

  public native void close() throws IOException;
}
