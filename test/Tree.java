import java.util.Comparator;
import java.util.TreeSet;

public class Tree {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static String printList(TreeSet<?> list) {
    StringBuilder sb = new StringBuilder();

    for (Object o : list) {
      sb.append(o);
      sb.append(", ");
    }
    sb.setLength(sb.length()-2);
    return sb.toString();
  }

  private static void isEqual(String s1, String s2) {
    System.out.println(s1);
    expect(s1.equals(s2));
  }

  private static class MyCompare implements Comparator<Integer> {
    public int compare(Integer o1, Integer o2) {
      return o1.compareTo(o2);
    }
  }

  public static void main(String args[]) {
    TreeSet<Integer> t1 = new TreeSet<Integer>(new MyCompare());
    t1.add(5); t1.add(2); t1.add(1); t1.add(8); t1.add(3);
    isEqual(printList(t1), "1, 2, 3, 5, 8");
    t1.add(4);
    isEqual(printList(t1), "1, 2, 3, 4, 5, 8");
    t1.remove(3);
    isEqual(printList(t1), "1, 2, 4, 5, 8");
    TreeSet<String> t2 = new TreeSet<String>(new Comparator<String>() {
      public int compare(String s1, String s2) {
        return s1.compareTo(s2);
      }
    });
    t2.add("one"); t2.add("two"); t2.add("three"); t2.add("four"); t2.add("five");
    isEqual(printList(t2), "five, four, one, three, two");
    for (int i=0; i < 1000; i++) {
      t2.add(Integer.toString(i));
    }
    expect(t2.size() == 1005);
    for (int i=0; i < 999; i++) {
      t2.remove(Integer.toString(i));
    }
    expect(t2.size() == 6);
    t2.add("kappa");
    isEqual(printList(t2), "999, five, four, kappa, one, three, two");
  }
}
