/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.net;

import java.io.File;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

public class URLClassLoader extends ClassLoader {

  private final File jarFile;

  public URLClassLoader(URL[] urls, ClassLoader parent) {
    super(parent);
    if(urls.length != 1) {
      throw new UnsupportedOperationException();
    }
    if(!urls[0].getProtocol().equals("file")) {
      throw new UnsupportedOperationException(urls[0].getProtocol());
    }
    this.jarFile = new File(urls[0].getFile());
  }


  protected Class findClass(String name) throws ClassNotFoundException {
    try {
      InputStream stream = getResourceAsStream(name.replace(".", "/") + ".class");
      if(stream == null) {
        throw new ClassNotFoundException("couldn't find class " + name);
      }
      byte[] buf = new byte[2048];
      ByteArrayOutputStream mem = new ByteArrayOutputStream();
      try {
        int size;
        while((size = stream.read(buf, 0, buf.length)) > 0) {
          mem.write(buf, 0, size);
        }
        byte[] data = mem.toByteArray();
        return defineClass(name, data, 0, data.length);
      } finally {
        stream.close();
      }
    } catch(IOException e) {
      throw new ClassNotFoundException("couldn't find class " + name, e);
    }
  }

  public URL getResource(String path) {
    try {
      return new URL("jar:file:" + jarFile.getAbsolutePath() + "!/" + path);
    } catch(MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }


}