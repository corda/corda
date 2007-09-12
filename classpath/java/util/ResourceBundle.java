package java.util;

import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.IOException;

public abstract class ResourceBundle {
  protected ResourceBundle parent;

  private static String replace(char a, char b, String s) {
    char[] array = new char[s.length()];
    for (int i = 0; i < array.length; ++i) {
      char c = s.charAt(i);
      array[i] = (c == a ? b : c);
    }
    return new String(array, 0, array.length, false);
  }

  private static ResourceBundle findProperties(String name, ClassLoader loader,
                                               ResourceBundle parent)
    throws IOException
  {
    InputStream in = loader.getResourceAsStream
      (replace('.', '/', name) + ".properties");
    if (in != null) {
      try {
        return new MapResourceBundle(new Parser().parse(in), parent);
      } finally {
        in.close();
      }
    } else {
      return null;
    }
  }

  private static ResourceBundle find(String name, ClassLoader loader,
                                     ResourceBundle parent)
    throws Exception
  {
    try {
      Class c = Class.forName(name, true, loader);
      if (c.isAssignableFrom(ResourceBundle.class)) {
        return (ResourceBundle) c.getConstructor().newInstance();
      }
    } catch (ClassNotFoundException ok) {
    } catch (NoSuchMethodException ok) { }

    return findProperties(name, loader, parent);
  }

  public static ResourceBundle getBundle(String name, Locale locale,
                                         ClassLoader loader)
  {
    try {
      ResourceBundle b = find(name, loader, null);
      if (locale.getLanguage() != null) {
        name = name + "_" + locale.getLanguage();
        b = find(name, loader, b);
        if (locale.getCountry() != null) {
          name = name + "_" + locale.getCountry();
          b = find(name, loader, b);
          if (locale.getVariant() != null) {
            name = name + "_" + locale.getVariant();
            b = find(name, loader, b);
          }
        }
      }
      return b;
    } catch (Exception e) {
      RuntimeException re = new MissingResourceException(name, name, null);
      re.initCause(e);
      throw re;
    }
  }

  public static ResourceBundle getBundle(String name, Locale locale) {
    return getBundle(name, locale,
                     Method.getCaller().getDeclaringClass().getClassLoader());
  }

  public static ResourceBundle getBundle(String name) {
    return getBundle(name, Locale.getDefault(),
                     Method.getCaller().getDeclaringClass().getClassLoader());
  }

  public Object getObject(String key) {
    for (ResourceBundle b = this; b != null; b = b.parent) {
      Object value = b.handleGetObject(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public String getString(String key) {
    return (String) getObject(key);
  }

  protected abstract Object handleGetObject(String key);

  private static class MapResourceBundle extends ResourceBundle {
    private final Map<String, Object> map;

    public MapResourceBundle(Map<String, Object> map, ResourceBundle parent) {
      this.parent = parent;
      this.map = map;
    }

    protected Object handleGetObject(String key) {
      return map.get(key);
    }
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

    private Map<String, Object> parse(InputStream in)
      throws IOException
    {
      Map<String, Object> map = new HashMap();
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
            if (escaped || key != current) {
              append(c);
            } else {
              current = null;
            }
            break;

          case ':':
          case '=':
            if (escaped || key != current) {
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

      return map;
    }
  }
}
