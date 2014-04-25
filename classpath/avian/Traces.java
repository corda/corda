/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Traces {
  private static final String Newline = System.getProperty("line.separator");

  private static String traceAllThreads() {
    StringBuilder buffer = new StringBuilder();

    Thread[] threads = new Thread[Thread.activeCount()];

    int count = Thread.enumerate(threads);
    for (int i = 0; i < count; ++i) {
      traceThread(threads[i], buffer);
    }

    return buffer.toString();
  }

  private static String traceThread(Thread thread) {
    StringBuilder buffer = new StringBuilder();

    traceThread(thread, buffer);

    return buffer.toString();
  }

  private static void traceThread(Thread thread, StringBuilder buffer) {
    buffer.append(thread).append(Newline);
    for (StackTraceElement e: thread.getStackTrace()) {
      buffer.append("\tat ").append(e).append(Newline);
    }
  }

  public static void startTraceListener(final String host, final int port) {
    Thread t = new Thread(new Runnable() {
        public void run() {
          try {
            ServerSocketChannel server = ServerSocketChannel.open();
            server.socket().bind(new InetSocketAddress(host, port));
            while (true) {
              SocketChannel c = server.accept();
              try {
                c.write(ByteBuffer.wrap(traceAllThreads().getBytes()));
              } finally {
                c.close();
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    t.setDaemon(true);
    t.start();
  }
}
