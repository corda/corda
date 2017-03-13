/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;

import avian.FormatString;

public class Formatter implements Closeable, Flushable, AutoCloseable {

  private final Appendable _out;
  
  private final Locale _locale;
   
  private IOException lastException;
  
  private boolean _closed;
 
  private void ensureNotClosed() {
    if (this._closed) {
      throw new IllegalStateException();
    }
  }
  
  public Formatter() {
    this((Appendable) null, (Locale) null);
  }
  
  public Formatter(final Appendable a) {
    this(a, null); 
  }
  
  public Formatter(final Appendable a, final Locale l) {
    if (a == null) {
      this._out = new StringBuilder();
    } else {
      this._out = a;
    }
    if (l == null) {
      this._locale = Locale.getDefault();
    } else {
      this._locale = l;
    }
  }
  
  public Formatter(File file) throws IOException {
    this(file, null);
  }
  
  public Formatter(File file, Locale l) throws IOException {
    this(new FileWriter(file), l);
  }
  
  public Formatter(Locale l) {
    this((Appendable) null, l);
  }
  
  public Formatter(OutputStream os) {
    this(os, null);
  }
  
  public Formatter(OutputStream os, Locale l) {
    this(new OutputStreamWriter(os), l);
  }
  
  public Formatter(PrintStream ps) {
    this(new OutputStreamWriter(ps));
  }
  
  public Formatter(String fileName) throws IOException {
    this(fileName, (Locale) null);
  }
  
  public Formatter(String fileName, Locale l) throws IOException {
    this(new File(fileName), l);
  }

  public void close() {
    if (this._closed) {
      return;
    }
    final Appendable out = out();
    if (out instanceof Closeable) {
      final Closeable closeable = (Closeable) out;
      try {
        closeable.close();
      } catch (IOException e) {
        throw new RuntimeException("An error occurred while closing.", e);
      }
      this._closed = true;
    }
  }

  public void flush() {
    ensureNotClosed();
    final Appendable out = out();
    if (out instanceof Flushable) {
      final Flushable flushable = (Flushable) out;
      try {
        flushable.flush();
      } catch (IOException e) {
        throw new RuntimeException("An error occurred while flushing.", e);
      }
    }
  }

  public Formatter format(Locale l, final String format, final Object...args) {
    ensureNotClosed();
    try {
      final FormatString formatString = FormatString.compile(format);
      this._out.append(formatString.format(args));
    } catch (IOException e) {
      this.lastException = e;
    }
    return this;
  }

  public Formatter format(String format, Object... args) {
    ensureNotClosed();
    return this.format(null, format, args);
  }

  public IOException ioException() {
    return lastException;
  }

  public Locale locale() {
    ensureNotClosed();
    return this._locale;
  }

  public Appendable out() {
    ensureNotClosed();
    return this._out;
  }

  public String toString() {
    ensureNotClosed();
    return this._out.toString();
  }

}
