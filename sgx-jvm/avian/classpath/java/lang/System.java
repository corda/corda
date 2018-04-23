/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.io.PrintStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.util.Map;
import java.util.Hashtable;
import java.util.Properties;

public abstract class System {
  private static class Static {
    public static Properties properties = makeProperties();
  }

  private static Map<String, String> environment;
  
  private static SecurityManager securityManager;
  //   static {
  //     loadLibrary("natives");
  //   }

  public static final PrintStream out = new PrintStream
    (new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), true);

  public static final PrintStream err = new PrintStream
    (new BufferedOutputStream(new FileOutputStream(FileDescriptor.err)), true);

  public static final InputStream in
    = new BufferedInputStream(new FileInputStream(FileDescriptor.in));

  public static native void arraycopy(Object src, int srcOffset, Object dst,
                                      int dstOffset, int length);

  public static String getProperty(String name) {
    return (String) Static.properties.get(name);
  }
  
  public static String getProperty(String name, String defaultValue) {
    String result = getProperty(name);
    if (result==null) {
      return defaultValue;
    }
    return result;
  }
  
  public static String setProperty(String name, String value) {
    return (String) Static.properties.put(name, value);
  }

  public static Properties getProperties() {
    return Static.properties;
  }

  private static Properties makeProperties() {
    Properties properties = new Properties();

    for (String p: getNativeProperties()) {
      if (p == null) break;
      int index = p.indexOf('=');
      properties.put(p.substring(0, index), p.substring(index + 1));
    }

    for (String p: getVMProperties()) {
      if (p == null) break;
      int index = p.indexOf('=');
      properties.put(p.substring(0, index), p.substring(index + 1));
    }

    return properties;
  }
  
  private static native String[] getNativeProperties();

  private static native String[] getVMProperties();

  public static native int identityHashCode(Object o);

  public static String mapLibraryName(String name) {
    if (name != null) {
      return doMapLibraryName(name);
    } else {
      throw new NullPointerException();
    }
  }

  private static native String doMapLibraryName(String name);

  public static void load(String path) {
    ClassLoader.load(path, ClassLoader.getCaller(), false);
  }

  public static void loadLibrary(String name) {
    ClassLoader.load(name, ClassLoader.getCaller(), true);
  }

  public static void gc() {
    Runtime.getRuntime().gc();
  }

  public static void exit(int code) {
    Runtime.getRuntime().exit(code);
  }
  
  public static SecurityManager getSecurityManager() {
    return securityManager;
  }
  
  public static void setSecurityManager(SecurityManager securityManager) {
    System.securityManager = securityManager;
  }

  public static String getenv(String name) throws NullPointerException,
    SecurityException {
    if (getSecurityManager() != null) { // is this allowed?
      getSecurityManager().
        checkPermission(new RuntimePermission("getenv." + name));
    }
    return getenv().get(name);
  }

  public static Map<String, String> getenv() throws SecurityException {
    if (getSecurityManager() != null) { // is this allowed?
      getSecurityManager().checkPermission(new RuntimePermission("getenv.*"));
    }

    if (environment == null) { // build environment table
      String[] vars = getEnvironment();
      environment = new Hashtable<String, String>(vars.length);
      for (String var : vars) { // parse name-value pairs
        int equalsIndex = var.indexOf('=');
        // null names and values are forbidden
        if (equalsIndex != -1 && equalsIndex < var.length() - 1) {
          environment.put(var.substring(0, equalsIndex),
                          var.substring(equalsIndex + 1));
        }
      }
    }

    return environment;
  }

  /** Returns the native process environment. */
  private static native String[] getEnvironment();
}
