import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import avian.testing.annotations.Color;
import avian.testing.annotations.Test;
import avian.testing.annotations.TestComplex;
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
    testProxyDefaultValue();
    testComplexAnnotation();
  }

  @Test("couscous")
  @TestEnum(Color.Red)
  @TestInteger(42)
  public static void foo() {
    
  }
  
  public static void noAnnotation() {
    
  }

  private static void testProxyDefaultValue() {
    ClassLoader loader = Annotations.class.getClassLoader();
    InvocationHandler handler = new InvocationHandler() {
      public Object invoke(Object proxy, Method method, Object... args) {
        return method.getDefaultValue();
      }
    };
    Test test = (Test)
      Proxy.newProxyInstance(loader, new Class[] { Test.class }, handler);
    expect("Hello, world!".equals(test.value()));
  }

  private interface World {
    @TestComplex(arrayValue = { @Test, @Test(value = "7/9") },
      stringValue = "adjunct element", charValue = '7', doubleValue = 0.7778,
      classValue = TestInteger.class)
    int hello();
  }

  private static void testComplexAnnotation(TestComplex annotation)
    throws Exception
  {
    expect(2 == annotation.arrayValue().length);
    expect("Hello, world!".equals(annotation.arrayValue()[0].value()));
    expect("7/9".equals(annotation.arrayValue()[1].value()));
    expect("adjunct element".equals(annotation.stringValue()));
    expect('7' == annotation.charValue());
    expect(0.7778 == annotation.doubleValue());
    expect(TestInteger.class == annotation.classValue());
  }

  public static void testComplexAnnotation() throws Exception {
    ClassLoader loader = Annotations.class.getClassLoader();
    TestComplex annotation = (TestComplex)
      World.class.getMethod("hello").getAnnotation(TestComplex.class);
    testComplexAnnotation(annotation);
    Class clazz = Proxy.getProxyClass(loader, new Class[] { World.class });
    annotation = (TestComplex)
      clazz.getMethod("hello").getAnnotation(TestComplex.class);
    expect(annotation == null);
  }
}
