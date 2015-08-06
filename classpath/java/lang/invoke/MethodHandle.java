package java.lang.invoke;

import avian.Classes;

public class MethodHandle {
  static final int REF_invokeStatic = 6;
  static final int REF_invokeSpecial = 7;
  
  final int kind;
  private final ClassLoader loader;
  final avian.VMMethod method;
  private volatile MethodType type;
  
  MethodHandle(int kind, ClassLoader loader, avian.VMMethod method) {
    this.kind = kind;
    this.loader = loader;
    this.method = method;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (method.class_ != null) {
      sb.append(Classes.makeString(method.class_.name, 0,
                                   method.class_.name.length - 1));
      sb.append(".");
    }
    sb.append(Classes.makeString(method.name, 0,
                                 method.name.length - 1));
    sb.append(Classes.makeString(method.spec, 0,
                                 method.spec.length - 1));
    return sb.toString();
  }  

  public MethodType type() {
    if (type == null) {
      type = new MethodType(loader, method.spec);
    }
    return type;
  }
}
