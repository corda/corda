/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class URLConnection {
  protected final URL url;
  protected boolean doInput = true;
  protected boolean doOutput = false;
  protected boolean useCaches = true;

  protected URLConnection(URL url) {
    this.url = url;
  }

  public Object getContent() throws IOException {
    return getInputStream();
  }

  public int getContentLength() {
    return -1;
  }

  public long getContentLengthLong() {
    return -1l;
  }

  public abstract void connect() throws IOException;

  public InputStream getInputStream() throws IOException {
    throw new UnknownServiceException();
  }

  public OutputStream getOutputStream() throws IOException {
    throw new UnknownServiceException();
  }

  public boolean getDoInput() {
    return doInput;
  }

  public boolean getDoOutput() {
    return doOutput;
  }

  public void setDoInput(boolean v) {
    doInput = v;
  }

  public void setDoOutput(boolean v) {
    doInput = v;
  }

  public void setUseCaches(boolean v) {
    useCaches = v;
  }

  public String getHeaderField(String name) {
    String result = null;
    if(name != null) {
      List<String> values = getHeaderFields().get(name.toLowerCase());
      if (values != null && values.size() > 0) {
        result = values.get(0);
      }
    }

    return result;
  }

  public int getHeaderFieldInt(String name, int Default) {
    return (int) getHeaderFieldLong(name, Default);
  }

  public long getHeaderFieldLong(String name, long Default) {
    long result = Default;
    try {
      result = Long.parseLong(getHeaderField(name));
    } catch(Exception e) {
      // Do nothing, default will be returned
    }

    return result;
  }

  public Map<String,List<String>> getHeaderFields() {
    return Collections.emptyMap();
  }
}
