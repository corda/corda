package java.io;

public class File {
  private final String path;

  public File(String path) {
    if (path == null) throw new NullPointerException();
    this.path = path;
  }

  public File(String parent, String child) {
    this(parent + "/" + child);
  }

  public File(File parent, String child) {
    this(parent.getPath() + "/" + child);
  }

  public String getName() {
    int index = path.lastIndexOf("/");
    if (index >= 0) {
      return path.substring(index + 1);
    } else {
      return path;
    }
  }

  public String getPath() {
    return path;
  }

  private static native String toAbsolutePath(String path);

  public String getAbsolutePath() {
    return toAbsolutePath(path);
  }

  private static native long length(String path);

  public long length() {
    return length(path);
  }

  private static native boolean exists(String path);

  public boolean exists() {
    return exists(path);
  }

  private static native void mkdir(String path);

  public void mkdir() {
    mkdir(path);
  }

  private static native void createNewFile(String path);

  public void createNewFile() {
    createNewFile(path);
  }
}
