package java.lang.invoke;

public class MethodHandles {
  public static class Lookup {
    final avian.VMClass class_;
    private final int modes;

    private Lookup(avian.VMClass class_, int modes) {
      this.class_ = class_;
      this.modes = modes;
    }

    public String toString() {
      return "lookup[" + avian.SystemClassLoader.getClass(class_) + ", "
        + modes + "]";
    }
  }
}
