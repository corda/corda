package java.io;

public class FileReader extends Reader {
  private final Reader in;

  public FileReader(FileInputStream in) {
    this.in = new InputStreamReader(in);
  }
  
  public FileReader(FileDescriptor fd) {
    this(new FileInputStream(fd));
  }

  public FileReader(String path) throws IOException {
    this(new FileInputStream(path));
  }

  public FileReader(File file) throws IOException {
    this(new FileInputStream(file));
  }

  public int read(char[] b, int offset, int length) throws IOException {
    return in.read(b, offset, length);
  }

  public void close() throws IOException {
    in.close();
  }
}
