package java.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class Properties extends Hashtable {
  public void load(InputStream in) throws IOException {
    new Parser().parse(in, this);
  }

  public void store(OutputStream out, String comment) throws IOException {
    // TODO
  }

  public String getProperty(String key) {
    return (String)get(key);
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
