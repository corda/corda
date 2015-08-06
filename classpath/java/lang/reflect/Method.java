/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

import avian.VMMethod;
import avian.AnnotationInvocationHandler;
import avian.SystemClassLoader;
import avian.Classes;

import java.lang.annotation.Annotation;

public class Method<T> extends AccessibleObject implements Member {
  public final VMMethod vmMethod;
  private boolean accessible;

  public Method(VMMethod vmMethod) {
    this.vmMethod = vmMethod;
  }

  public boolean equals(Object o) {
    return o instanceof Method && ((Method) o).vmMethod == vmMethod;
  }

  public boolean isAccessible() {
    return accessible;
  }

  public void setAccessible(boolean v) {
    accessible = v;
  }

  public static native VMMethod getCaller();

  public Class<T> getDeclaringClass() {
    return SystemClassLoader.getClass(vmMethod.class_);
  }

  public int getModifiers() {
    return vmMethod.flags;
  }

  public String getName() {
    return getName(vmMethod);
  }

  public static String getName(VMMethod vmMethod) {
    return Classes.makeString(vmMethod.name, 0, vmMethod.name.length - 1);
  }

  private String getSpec() {
    return getSpec(vmMethod);
  }
  
  public static String getSpec(VMMethod vmMethod) {
    return Classes.makeString(vmMethod.spec, 0, vmMethod.spec.length - 1);
  }

  public Class[] getParameterTypes() {
    return Classes.getParameterTypes(vmMethod);
  }

  public Object invoke(Object instance, Object ... arguments)
    throws InvocationTargetException, IllegalAccessException
  {
    if ((vmMethod.flags & Modifier.STATIC) != 0
        || Class.isInstance(vmMethod.class_, instance))
    {
      if ((vmMethod.flags & Modifier.STATIC) != 0) {
        instance = null;
      }

      if (arguments == null) {
        if (vmMethod.parameterCount > 0) {
          throw new NullPointerException();
        }
        arguments = new Object[0];
      }

      if (arguments.length == vmMethod.parameterCount) {
        Classes.initialize(vmMethod.class_);

        return invoke(vmMethod, instance, arguments);        
      } else {
        throw new ArrayIndexOutOfBoundsException();
      }
    } else {
//       System.out.println
//         (getDeclaringClass() + "." + getName() + " flags: " + vmMethod.flags + " vm flags: " + vmMethod.vmFlags + " return code: " + vmMethod.returnCode);
      throw new IllegalArgumentException();
    }
  }

  private static native Object invoke(VMMethod method, Object instance,
                                      Object ... arguments)
    throws InvocationTargetException, IllegalAccessException;

  public Class getReturnType() {
    for (int i = 0; i < vmMethod.spec.length - 1; ++i) {
      if (vmMethod.spec[i] == ')') {
        return Classes.forCanonicalName
          (vmMethod.class_.loader,
           Classes.makeString
           (vmMethod.spec, i + 1, vmMethod.spec.length - i - 2));
      }
    }
    throw new RuntimeException();
  }

  public <T extends Annotation> T getAnnotation(Class<T> class_) {
    if (vmMethod.hasAnnotations()) {
      Object[] table = (Object[]) vmMethod.addendum.annotationTable;
      for (int i = 0; i < table.length; ++i) {
        Object[] a = (Object[]) table[i];
        if (a[1] == class_) {
          return (T) Classes.getAnnotation(vmMethod.class_.loader, a);
        }
      }
    }
    return null;
  }

  public Annotation[] getAnnotations() {
    if (vmMethod.hasAnnotations()) {
      Object[] table = (Object[]) vmMethod.addendum.annotationTable;
      Annotation[] array = new Annotation[table.length];
      for (int i = 0; i < table.length; ++i) {
        array[i] = Classes.getAnnotation
          (vmMethod.class_.loader, (Object[]) table[i]);
      }
      return array;
    } else {
      return new Annotation[0];
    }
  }

  public Annotation[] getDeclaredAnnotations() {
    return getAnnotations();
  }

  public boolean isVarArgs() {
    return (getModifiers() & ACC_VARARGS) != 0;
  }

  public boolean isSynthetic() {
    return (getModifiers() & ACC_SYNTHETIC) != 0;
  }

  public Object getDefaultValue() {
    ClassLoader loader = getDeclaringClass().getClassLoader();
    return Classes.getAnnotationDefaultValue(loader, vmMethod.addendum);
  }

  public Class<?>[] getExceptionTypes() {
    throw new UnsupportedOperationException("not yet implemented");
  }
}
