package java.lang;

public abstract class ClassLoader {
  private final ClassLoader parent;

  protected ClassLoader(ClassLoader parent) {
    if (parent == null) {
      this.parent = getSystemClassLoader();
    } else {
      this.parent = parent;
    }
  }

  protected ClassLoader() {
    this(getSystemClassLoader());
  }

  public static ClassLoader getSystemClassLoader() {
    return ClassLoader.class.getClassLoader();
  }

  private static native Class defineClass(byte[] b, int offset, int length);

  protected Class defineClass(String name, byte[] b, int offset, int length) {
    if (b == null) {
      throw new NullPointerException();
    }

    if (offset < 0 || offset > length || offset + length > b.length) {
      throw new IndexOutOfBoundsException();
    }

    return defineClass(b, offset, length);
  }

  protected Class findClass(String name) throws ClassNotFoundException {
    throw new ClassNotFoundException();
  }

  protected Class findLoadedClass(String name) {
    return null;
  }

  public Class loadClass(String name) throws ClassNotFoundException {
    return loadClass(name, false);
  }

  protected Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    Class c = findLoadedClass(name);
    if (c == null) {
      if (parent != null) {
        try {
          c = parent.loadClass(name);
        } catch (ClassNotFoundException ok) { }
      }

      if (c == null) {
        c = findClass(name);
      }
    }

    if (resolve) {
      resolveClass(c);
    }

    return c;
  }

  protected void resolveClass(Class c) {
    // ignore
  }

  private ClassLoader getParent() {
    return parent;
  }
}
