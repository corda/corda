package java.lang.invoke;

public class MethodHandle {
  private final ClassLoader loader;
  final avian.VMMethod method;
  private volatile MethodType type;
  
  MethodHandle(ClassLoader loader, avian.VMMethod method) {
    this.loader = loader;
    this.method = method;
  }
  
  public String toString() {
    return new java.lang.reflect.Method(method).toString();
  }

  public MethodType type() {
    if (type == null) {
      type = new MethodType(loader, method.spec);
    }
    return type;
  }
}
