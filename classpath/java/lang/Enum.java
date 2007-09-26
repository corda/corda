package java.lang;

import java.lang.reflect.Method;

public abstract class Enum<E extends Enum<E>> {
  private final String name;
  private final int ordinal;

  public Enum(String name, int ordinal) {
    this.name = name;
    this.ordinal = ordinal;
  }

  public int compareTo(E other) {
    return ordinal - other.ordinal;
  }

  public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) {
    if (name != null) {
      try {
        Method method = enumType.getMethod("values");
        Enum values[] = (Enum[])(method.invoke(null));
        for (Enum value : values) {
          if (name.equals(value.name)) {
            return (T) value;
          }
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    return null;
  }

  public int ordinal() {
    return ordinal;
  }
}
