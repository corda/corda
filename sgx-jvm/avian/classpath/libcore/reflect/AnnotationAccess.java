package libcore.reflect;

import java.lang.reflect.AccessibleObject;

public class AnnotationAccess {
  public static native AccessibleObject getEnclosingMethodOrConstructor
    (Class<?> c);
}
