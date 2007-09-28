package java.util;

public class TreeSet<T> implements Iterable<T> {
  private final Comparator<T> comparator;
  private int size;
  private Cell<T> root;

  public TreeSet(Comparator<T> comparator) {
    this.comparator = comparator;
    size=0;
    root=null;
  }

  public Iterator<T> iterator() {
    return walk().iterator();
  }

  private ArrayList<T> walk() {
    return walk(root, new ArrayList<T>(size));
  }

  private ArrayList<T> walk(Cell<T> cell, ArrayList<T> list) {
    if (cell != null) {
      walk(cell.left, list);
      list.add(cell.value);
      walk(cell.right, list);
    }
    return list;
  }

  public boolean add(T o) {
    ++size;
    if (root == null) {
      root = new Cell<T>(o);
      return true;
    } else {
      Cell<T> newElt = new Cell<T>(o);
      Cell<T> cur = root;
      do {
        int result = comparator.compare(o, cur.value);
        if (result == 0) return false;
        if (result < 0) {
          if (cur.left == null) {
            newElt.parent = cur;
            cur.left = newElt;
            return false;
          } else {
            cur = cur.left;
          }
        } else {
          if (cur.right == null) {
            newElt.parent = cur;
            cur.right = newElt;
            return false;
          } else {
            cur = cur.right;
          }
        }
      } while (cur != null);
      throw new RuntimeException("Fell off end of TreeSet");
    }
  }

  public boolean remove(T o) {
    throw new UnsupportedOperationException();
  }

  public int size() {
    return size;
  }

  private static class Cell<T> {
    public final T value;
    public Cell<T> parent;
    public Cell<T> left;
    public Cell<T> right;
    public Cell(T val) {
      value = val;
    }
  }
}
