/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;

public class Properties extends Hashtable {
  public void load(InputStream in) throws IOException {
    new Parser().parse(in, this);
  }

  public void store(OutputStream out, String comment) throws IOException {
    PrintStream os = new PrintStream(out);
    os.println("# " + comment);
    for (Iterator it = entrySet().iterator();
         it.hasNext();) {
      Map.Entry entry = (Map.Entry)it.next();
      os.print(entry.getKey());
      os.print('=');
      os.println(entry.getValue());
    }
    os.flush();
  }

  public String getProperty(String key) {
    return (String)get(key);
  }

  public String getProperty(String key, String defaultValue) {
    String value = (String) get(key);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  public void setProperty(String key, String value) {
    put(key, value);
  }

  private static class Parser {
    private StringBuilder key = null;
    private StringBuilder value = null;
    private StringBuilder current = null;

    private void append(int c) {
      if (current == null) {
        if (key == null) {
          current = key = new StringBuilder();
        } else {
          current = value = new StringBuilder();
        }
      }

      current.append((char) c);
    }

    private void finishLine(Map<String, Object> map) {
      if (key != null) {
        map.put(key.toString(),
                (value == null ? "" : value.toString().trim()));
      }

      key = value = current = null;
    }

    private void parse(InputStream in, Map map)
      throws IOException
    {
      boolean escaped = false;

      int c;
      while ((c = in.read()) != -1) {
        if (c == '\\') {
          if (escaped) {
            escaped = false;
            append(c);
          } else {
            escaped = true;
          }
        } else {
          switch (c) {
          case '#':
          case '!':
            if (key == null) {
              while ((c = in.read()) != -1 && c != '\n');
            } else {
              append(c);
            }
            break;

          case ' ':
          case '\r':
          case '\t':
            if (escaped || (current != null && value == current)) {
              append(c);
            } else if (key == current) {
              current = null;
            }
            break;

          case ':':
          case '=':
            if (escaped || (current != null && value == current)) {
              append(c);
            } else {
              if (key == null) {
                key = new StringBuilder();
              }
              current = null;
            }
            break;

          case '\n':
            if (escaped) {
              append(c);
            } else {
              finishLine(map);          
            }
            break;
          case 'n':
            if (escaped) {
              append('\n');
            } else {
              append(c);
            }
            break;

          default:
            append(c);
            break;
          }
        
          escaped = false;
        }
      }

      finishLine(map);
    }
  }
}
