/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class SystemClassLoader extends ClassLoader {
  private native VMClass findVMClass(String name)
    throws ClassNotFoundException;

  protected Class findClass(String name) throws ClassNotFoundException {
    return getClass(findVMClass(name));
  }

  public static native Class getClass(VMClass vmClass);

  public static native VMClass vmClass(Class jClass);

  private native VMClass findLoadedVMClass(String name);

  protected Class reallyFindLoadedClass(String name){
    VMClass c = findLoadedVMClass(name);
    return c == null ? null : getClass(c);
  }

  protected Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    Class c = findLoadedClass(name);
    if (c == null) {
      ClassLoader parent = getParent();
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

  private native String resourceURLPrefix(String name);

  protected URL findResource(String name) {
    String prefix = resourceURLPrefix(name);
    if (prefix != null) {
      try {
        return new URL(prefix + name);
      } catch (MalformedURLException ignored) { }
    }
    return null;
  }

  protected Package getPackage(String name) {
    Package p = super.getPackage(name);
    if (p == null) {
      String source = getPackageSource(name);
      if (source != null) {
        // todo: load attributes from JAR manifest
        definePackage(name, null, null, null, null, null, null, null);
      }
    }

    return super.getPackage(name);
  }

  protected static native String getPackageSource(String name);

  // OpenJDK's java.lang.ClassLoader.getResource makes use of
  // sun.misc.Launcher to load bootstrap resources, which is not
  // appropriate for the Avian build, so we override it to ensure we
  // get the behavior we want.  This implementation is the same as
  // that of Avian's java.lang.ClassLoader.getResource.
  public URL getResource(String path) {
    URL url = null;
    ClassLoader parent = getParent();
    if (parent != null) {
      url = parent.getResource(path);
    }

    if (url == null) {
      url = findResource(path);
    }

    return url;
  }

  // As above, we override this method to avoid inappropriate behavior
  // in OpenJDK's java.lang.ClassLoader.getResources.
  public Enumeration<URL> getResources(String name) throws IOException {
    Collection<URL> urls = new ArrayList<URL>(5);

    ClassLoader parent = getParent();
    if (parent != null) {
      for (Enumeration<URL> e = parent.getResources(name);
           e.hasMoreElements();)
      {
        urls.add(e.nextElement());
      }
    }

    Enumeration<URL> urls2 = findResources(name);
    while (urls2.hasMoreElements()) {
      urls.add(urls2.nextElement());
    }

    return Collections.enumeration(urls);
  }

  private class ResourceEnumeration implements Enumeration<URL> {
    private long[] finderElementPtrPtr;
    private String name, urlPrefix;

    public ResourceEnumeration(String name) {
      this.name = name;
      finderElementPtrPtr = new long[1];
      urlPrefix = nextResourceURLPrefix();
    }

    private native String nextResourceURLPrefix(SystemClassLoader loader,
      String name, long[] finderElementPtrPtr);

    private String nextResourceURLPrefix() {
      return nextResourceURLPrefix(SystemClassLoader.this, name,
        finderElementPtrPtr);
    }

    public boolean hasMoreElements() {
      return urlPrefix != null;
    }

    public URL nextElement() {
      if (urlPrefix == null) throw new NoSuchElementException();
      URL result;
      try {
        result = new URL(urlPrefix + name);
      } catch (MalformedURLException ignored) {
        result = null;
      }
      if (finderElementPtrPtr[0] == 0l) urlPrefix = null;
      else urlPrefix = nextResourceURLPrefix();
      return result;
    }
  }

  protected Enumeration<URL> findResources(String name) {
    return new ResourceEnumeration(name);
  }
}
