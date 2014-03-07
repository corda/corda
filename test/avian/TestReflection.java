package avian;

import java.lang.reflect.Field;

public class TestReflection {
  public static Object get(Field field, Object target) throws Exception {
    return field.get(target);
  }
}
