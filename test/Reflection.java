import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class Reflection {
  public static void main(String[] args) throws Exception {
    Class system = Class.forName("java.lang.System");
    Field out = system.getDeclaredField("out");
    Class output = Class.forName("java.lang.System$Output");
    Method println = output.getDeclaredMethod("println", String.class);

    println.invoke(out.get(null), "Hello, World!");
  }
}
