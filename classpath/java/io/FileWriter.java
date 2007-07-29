package java.io;

public class FileWriter extends Writer {
  private final Writer out;

  private FileWriter(FileOutputStream out) {
    this.out = new OutputStreamWriter(out);
  }

  public FileWriter(FileDescriptor fd) {
    this(new FileOutputStream(fd));
  }

  public FileWriter(String path) throws IOException {
    this(new FileOutputStream(path));
  }

  public FileWriter(File file) throws IOException {
    this(new FileOutputStream(file));
  }
  
  public void write(char[] b, int offset, int length) throws IOException {
    out.write(b, offset, length);
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }
}
