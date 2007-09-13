package java.io;

public class FileDescriptor {
  public static final FileDescriptor in = new FileDescriptor(0);
  public static final FileDescriptor out = new FileDescriptor(1);
  public static final FileDescriptor err = new FileDescriptor(2);

  final int value;

  public FileDescriptor(int value) {
    this.value = value;
  }

  public FileDescriptor() {
    this(-1);
  }
}
