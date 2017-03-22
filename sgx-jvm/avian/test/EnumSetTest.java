import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class EnumSetTest {
  private enum SmallEnum {
    ONE,
    TWO,
    THREE
  }
  
  private enum LargerEnum {
    LARGEONE,
    LARGETWO,
    LARGETHREE,
    LARGEFOUR,
    LARGEFIVE,
    LARGESIX
  }
  
  public static void main(String[] args) {
    testAllOf();
    testNoneOf();
    testIterators();
    testOf();
    testCopyOf();
    testComplimentOf();
  }
  
  private static void testComplimentOf() {
    EnumSet<SmallEnum> one = EnumSet.of(SmallEnum.ONE, SmallEnum.THREE);
    EnumSet<SmallEnum> two = EnumSet.complementOf(one);
    assertElementInSet(SmallEnum.TWO, two);
    assertSize(1, two);
  }

  private static void testCopyOf() {
    EnumSet<SmallEnum> one = EnumSet.of(SmallEnum.ONE, SmallEnum.THREE);
    EnumSet<SmallEnum> two = EnumSet.copyOf(one);
    assertElementInSet(SmallEnum.ONE, two);
    assertElementInSet(SmallEnum.THREE, two);
    assertSize(2, two);
  }

  private static void testOf() {
    EnumSet<LargerEnum> set = EnumSet.of(LargerEnum.LARGEONE, LargerEnum.LARGEFIVE, LargerEnum.LARGETWO);
    assertElementInSet(LargerEnum.LARGEONE, set);
    assertElementInSet(LargerEnum.LARGEFIVE, set);
    assertElementInSet(LargerEnum.LARGETWO, set);
    assertSize(3, set);
  }

  private static void testAllOf() {
    EnumSet<SmallEnum> set = EnumSet.allOf(SmallEnum.class);
    for (SmallEnum current : SmallEnum.values()) {
      assertElementInSet(current, set);
    }
    assertSize(3, set);
  }
  
  private static void testNoneOf() {
    EnumSet<SmallEnum> set = EnumSet.noneOf(SmallEnum.class);
    assertSize(0, set);
  }
  
  private static void testIterators() {
    EnumSet<SmallEnum> set = EnumSet.allOf(SmallEnum.class);
    Iterator<SmallEnum> iterator = set.iterator();
    boolean exceptionCaught = false;
    try {
      iterator.remove();
    } catch (IllegalStateException e) {
      exceptionCaught = true;
    }
    if (!exceptionCaught) {
      throw new RuntimeException("Calling remove() before next() should throw IllegalStateException");
    }
    
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
    assertSize(0, set);

    exceptionCaught = false;
    try {
      iterator.next();
    } catch (NoSuchElementException e) {
      exceptionCaught = true;
    }
    if (!exceptionCaught) {
      throw new RuntimeException("Calling next() when hasNext() == false should throw NoSuchElementException");
    }
  }
  
  private static void assertElementInSet(Enum<?> element, EnumSet<?> set) {
    if (!set.contains(element)) {
      throw new RuntimeException("expected " + element + " in the set!");
    }
  }
  
  private static void assertSize(int expectedSize, EnumSet<?> set) {
    if (set.size() != expectedSize) {
      throw new RuntimeException("expected the set to be size=" + expectedSize + ", actual=" + set.size());
    }
  }
}
