import java.lang.reflect.Method;

import avian.testing.annotations.Color;
import avian.testing.annotations.Test;
import avian.testing.annotations.TestEnum;
import avian.testing.annotations.TestInteger;

public class Annotations {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    Method m = Annotations.class.getMethod("foo");

    expect(m.isAnnotationPresent(Test.class));

    expect(((Test) m.getAnnotation(Test.class)).value().equals("couscous"));

    expect(((TestEnum) m.getAnnotation(TestEnum.class)).value()
           .equals(Color.Red));

    expect(((TestInteger) m.getAnnotation(TestInteger.class)).value() == 42);
    
    expect(m.getAnnotations().length == 3);
    
    Method noAnno = Annotations.class.getMethod("noAnnotation");
    expect(noAnno.getAnnotation(Test.class) == null);
    expect(noAnno.getAnnotations().length == 0);
  }

  @Test("couscous")
  @TestEnum(Color.Red)
  @TestInteger(42)
  public static void foo() {
    
  }
  
  public static void noAnnotation() {
    
  }
}
