package java.lang;

public final class Class <T> {
  private short flags;
  private short vmFlags;
  private short fixedSize;
  private short arrayElementSize;
  private int[] objectMask;
  private byte[] name;
  private Class super_;
  private Object interfaceTable;
  private Object virtualTable;
  private Object fieldTable;
  private Object methodTable;
  private Object staticTable;
  private Object initializer;

  private Class() { }

  public String getName() {
    return new String(name, 0, name.length, false);
  }
}
