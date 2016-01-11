package avian.testing;

import java.util.Collection;

public class Asserts {

  public static void assertEquals(byte first, byte second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertEquals(short first, short second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertEquals(int first, int second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertEquals(long first, long second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertEquals(float first, float second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertEquals(double first, double second) {
    if(first != second) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  
  
  
  
  public static void assertEquals(Object first, Object second) {
    if(first == null && second == null) {
      return;
    }
    if(!first.equals(second)) {
      throw new RuntimeException(first+" is not equals to: "+second);
    }
  }
  
  public static void assertTrue(boolean flag) {
    if (!flag) {
      throw new RuntimeException("Error: "+flag+" is not True");
    }
  }
  
  public static void assertContains(Enum<?> element, Collection<?> collection) {
    if (!collection.contains(element)) {
      throw new RuntimeException("expected " + element + " in the collection:"+collection);
    }
  }
}
