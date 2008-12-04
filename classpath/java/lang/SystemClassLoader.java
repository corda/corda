/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.net.URL;
import java.net.MalformedURLException;

public class SystemClassLoader extends ClassLoader {
  protected native Class findClass(String name) throws ClassNotFoundException;

  protected native Class findLoadedClass(String name);

  private native boolean resourceExists(String name);

  protected URL findResource(String name) {
    if (resourceExists(name)) {
      try {
        return new URL("resource:" + name);
      } catch (MalformedURLException ignored) { }
    }
    return null;
  }
}
