/* Copyright (c) 2008-2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Enumeration;

public class SystemClassLoader extends ClassLoader {
  private native VMClass findVMClass(String name)
    throws ClassNotFoundException;

  protected Class findClass(String name) throws ClassNotFoundException {
    return getClass(findVMClass(name));
  }

  public static native Class getClass(VMClass vmClass);

  private native VMClass findLoadedVMClass(String name);

  protected Class reallyFindLoadedClass(String name){
    VMClass c = findLoadedVMClass(name);
    return c == null ? null : getClass(c);
  }

  private native boolean resourceExists(String name);

  protected URL findResource(String name) {
    if (resourceExists(name)) {
      try {
        return new URL("resource:" + name);
      } catch (MalformedURLException ignored) { }
    }
    return null;
  }

  protected Enumeration<URL> findResources(String name) {
    Collection<URL> urls = new ArrayList(1);
    URL url = findResource(name);
    if (url != null) {
      urls.add(url);
    }
    return Collections.enumeration(urls);
  }
}
