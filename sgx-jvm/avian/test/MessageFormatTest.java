import java.text.MessageFormat;

public class MessageFormatTest {

  private static void assertEquals(Object a, Object b) {
    if(!a.equals(b)) {
      throw new RuntimeException("[" + a + "] != [" + b + "]");
    }
  }

  public static void main(String[] args) {
    assertEquals("Hi there", MessageFormat.format("Hi there", "a"));
    assertEquals("Hi there", MessageFormat.format("Hi {0}here", "t"));
    assertEquals("Hi a!a!a", MessageFormat.format("Hi {0}!{0}!{0}", "a"));
    assertEquals("Hi There", MessageFormat.format("{1} {0}", "There", "Hi"));
    assertEquals("6 There 4", MessageFormat.format("{1} {2} {0}", 4, 6, "There"));
    assertEquals("Zero and {0} aren't the same", MessageFormat.format("{0} and '{0}' aren''t the same","Zero"));
    assertEquals("There are six grapes", MessageFormat.format("There are {0} grapes", "six"));
    assertEquals("3 + 2 = 5", MessageFormat.format("{2} + {1} = {0}", 5, 2, 3));
    assertEquals("again and again and again", MessageFormat.format("{0} and {0} and {0}", "again"));
    assertEquals("Joe's age is 30, not {0}", MessageFormat.format("Joe''s age is {0}, not '{0}'", 30));
  }

}