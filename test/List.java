import java.util.ArrayList;

public class List {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static String printList(ArrayList<Integer> list) {
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

  public static void main(String args[]) {
    ArrayList<Integer> l = new ArrayList<Integer>();
    l.add(1); l.add(2); l.add(3); l.add(4); l.add(5);
    isEqual(printList(l), "1, 2, 3, 4, 5");
    l.add(0, 6);
    isEqual(printList(l), "6, 1, 2, 3, 4, 5");
    l.add(2, 7);
    isEqual(printList(l), "6, 1, 7, 2, 3, 4, 5");
    l.remove(1);
    isEqual(printList(l), "6, 7, 2, 3, 4, 5");
    l.add(6, 8);
    isEqual(printList(l), "6, 7, 2, 3, 4, 5, 8");
    Integer[] ints = new Integer[15];
    Integer[] z = l.toArray(ints);
    expect(z == ints);
    for (int i=0; i < z.length; i++) {
      System.out.println(z[i]);
    }
  }
}
