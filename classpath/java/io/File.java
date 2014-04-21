/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class File implements Serializable {
  private static final String FileSeparator
    = System.getProperty("file.separator");

  public static final String separator = FileSeparator;

  public static final char separatorChar = FileSeparator.charAt(0);

  private static final String PathSeparator
    = System.getProperty("path.separator");

  public static final String pathSeparator = PathSeparator;

  public static final char pathSeparatorChar = PathSeparator.charAt(0);

  //   static {
  //     System.loadLibrary("natives");
  //   }

  private final String path;

  public File(String path) {
    if (path == null) throw new NullPointerException();
    this.path = normalize(path);
  }

  public File(String parent, String child) {
    this(parent + FileSeparator + child);
  }

  public File(File parent, String child) {
    this(parent.getPath() + FileSeparator + child);
  }

  public static File createTempFile(String prefix, String suffix)
    throws IOException
  {
    return createTempFile(prefix, suffix, null);
  }

  public static File createTempFile(String prefix, String suffix,
                                    File directory)
    throws IOException
  {
    if(directory == null) {
      directory = new File(System.getProperty("java.io.tmpdir"));
    }
    if(suffix == null) {
      suffix = ".tmp";
    }
    File ret;
    long state = System.currentTimeMillis();

    do {
      ret = generateFile(directory, prefix, state, suffix);
      state *= 7;
      state += 3;
    } while(ret == null);
    ret.createNewFile();
    return ret;
  }

  private static File generateFile(File directory, String prefix, long state, String suffix) {
    File ret = new File(directory, prefix + state + suffix);
    if(ret.exists()) {
      return null;
    } else {
      return ret;
    }
  }

  private static String stripSeparators(String p) {
    while (p.endsWith(FileSeparator)) {
      p = p.substring(0, p.length() - 1);
    }
    return p;
  }

  private static String normalize(String path) {
    return stripSeparators
      ("\\".equals(FileSeparator) ? path.replace('/', '\\') : path);
  }

  public static native boolean rename(String old, String new_);

  public boolean renameTo(File newName) {
    return rename(path, newName.path);
  }

  private static native boolean isDirectory(String path);

  public boolean isDirectory() {
    return isDirectory(path);
  }

  private static native boolean isFile(String path);

  public boolean isFile() {
    return isFile(path);
  }
  
  public boolean isAbsolute() {
    return path.equals(toAbsolutePath(path));
  }
  
  private static native boolean canRead(String path);
  
  public boolean canRead() {
    return canRead(path);
  }

  private static native boolean canWrite(String path);
  
  public boolean canWrite() {
    return canWrite(path);
  }

  private static native boolean canExecute(String path);

  public boolean canExecute() {
    return canExecute(path);
  }

  private static native boolean setExecutable(String path, boolean executable, boolean ownerOnly);

  public boolean setExecutable(boolean executable) {
    return setExecutable(executable, true);
  }

  public boolean setExecutable(boolean executable, boolean ownerOnly) {
    return setExecutable(path, executable, ownerOnly);
  }
  
  public String getName() {
    int index = path.lastIndexOf(FileSeparator);
    if (index >= 0) {
      return path.substring(index + 1);
    } else {
      return path;
    }
  }

  public String toString() {
    return getPath();
  }

  public String getPath() {
    return path;
  }

  public String getParent() {
    int index = path.lastIndexOf(FileSeparator);
    if (index >= 0) {
      return normalize(path.substring(0, index));
    } else {
      return null;
    }    
  }

  public File getParentFile() {
    String s = getParent();
    return (s == null ? null : new File(s));
  }

  private static native String toCanonicalPath(String path);

  public String getCanonicalPath() {
    return toCanonicalPath(path);
  }

  public File getCanonicalFile() {
    return new File(getCanonicalPath());
  }

  private static native String toAbsolutePath(String path);

  public String getAbsolutePath() {
    return toAbsolutePath(path);
  }

  public File getAbsoluteFile() {
    return new File(getAbsolutePath());
  }

  private static native long length(String path);

  public long length() {
    return length(path);
  }

  private static native boolean exists(String path);

  public boolean exists() {
    return exists(path);
  }

  private static native void mkdir(String path) throws IOException;

  public boolean mkdir() {
    try {
      mkdir(path);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static native boolean createNewFile(String path) throws IOException;

  public boolean createNewFile() throws IOException {
    return createNewFile(path);
  }

  public static native void delete(String path) throws IOException;

  public boolean delete() {
    try {
      delete(path);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public boolean mkdirs() {
    File parent = getParentFile();
    if (parent != null) {
      if (!parent.exists()) {
        if (!parent.mkdirs()) {
          return false;
        }
      }
    }
    return mkdir();
  }

  public File[] listFiles() {
    return listFiles(null);
  }

  public File[] listFiles(FilenameFilter filter) {
    String[] list = list(filter);
    if (list != null) {
      File[] result = new File[list.length];
      for (int i = 0; i < list.length; ++i) {
        result[i] = new File(this, list[i]);
      }
      return result;
    } else {
      return null;
    }
  }

  public String[] list() {
    return list(null);
  }

  public String[] list(FilenameFilter filter) {
    long handle = 0;
    try {
      handle = openDir(path);
      if (handle != 0) {
        Pair list = null;
        int count = 0;
        for (String s = readDir(handle); s != null; s = readDir(handle)) {
          if (filter == null || filter.accept(this, s)) {
            list = new Pair(s, list);
            ++ count;
          }
        }

        String[] result = new String[count];
        for (int i = count - 1; i >= 0; --i) {
          result[i] = list.value;
          list = list.next;
        }

        return result;
      } else {
        return null;
      }
    } finally {
      if (handle != 0) {
        closeDir(handle);
      }
    }
  }

  public long lastModified() {
    return lastModified(path);
  }
  private static native long openDir(String path);

  private static native long lastModified(String path);

  private static native String readDir(long handle);

  private static native long closeDir(long handle);



  private static class Pair {
    public final String value;
    public final Pair next;
    
    public Pair(String value, Pair next) {
      this.value = value;
      this.next = next;
    }
  }

}
