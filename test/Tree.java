import java.util.Comparator;
import java.util.TreeSet;

public class Tree {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static String printList(TreeSet<Integer> list) {
    StringBuilder sb = new StringBuilder();

    for (Integer i : list) {
      sb.append(i);
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
    TreeSet<Integer> l = new TreeSet<Integer>(new MyCompare());
    l.add(5); l.add(2); l.add(1); l.add(8); l.add(3);
    isEqual(printList(l), "1, 2, 3, 5, 8");
    l.add(4);
    isEqual(printList(l), "1, 2, 3, 4, 5, 8");
    l.remove(3);
    isEqual(printList(l), "1, 2, 4, 5, 8");
  }
}
