/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.util.StringTokenizer;

public class Runtime {
  private static final Runtime instance = new Runtime();

  private Runtime() { }

  public static Runtime getRuntime() {
    return instance;
  }

  public void load(String path) {
    if (path != null) {
      load(path, false);
    } else {
      throw new NullPointerException();
    }
  }

  public void loadLibrary(String path) {
    if (path != null) {
      load(path, true);
    } else {
      throw new NullPointerException();
    }
  }

  public Process exec(String command) throws IOException {
    long[] process = new long[4];
    StringTokenizer t = new StringTokenizer(command);
    String[] cmd = new String[t.countTokens()];
    for (int i = 0; i < cmd.length; i++)
      cmd[i] = t.nextToken();
    exec(cmd, process);
    return new MyProcess(process[0], (int) process[1], (int) process[2], (int) process[3]);
  }

  public Process exec(String[] command) {
    long[] process = new long[4];
    exec(command, process);
    return new MyProcess(process[0], (int) process[1], (int) process[2], (int) process[3]);
  }

  private static native void exec(String[] command, long[] process);

  private static native int exitValue(long pid);

  private static native int waitFor(long pid);

  private static native void load(String name, boolean mapName);

  public native void gc();

  public native void exit(int code);

  public native long freeMemory();

  public native long totalMemory();

  private static class MyProcess extends Process {
    private long pid;
    private final int in;
    private final int out;
    private final int err;
    private int exitCode;

    public MyProcess(long pid, int in, int out, int err) {
      this.pid = pid;
      this.in = in;
      this.out = out;
      this.err = err;
    }

    public void destroy() {
      throw new RuntimeException("not implemented");
    }

    public int exitValue() {
      if (pid != 0) {
        exitCode = Runtime.exitValue(pid);
      }
      return exitCode;
    }

    public InputStream getInputStream() {
      return new FileInputStream(new FileDescriptor(in));
    }

    public OutputStream getOutputStream() {
      return new FileOutputStream(new FileDescriptor(out));
    }

    public InputStream getErrorStream() {
      return new FileInputStream(new FileDescriptor(err));
    }

    public int waitFor() throws InterruptedException {
      if (pid != 0) {
        exitCode = Runtime.waitFor(pid);
        pid = 0;
      }
      return exitCode;
    }
  }
}
