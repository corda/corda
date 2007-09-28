package java.util;

import java.util.Comparator;
import java.lang.Iterable;

public class PersistentSet <T> implements Iterable <T> {
  private static final Node NullNode = new Node(null);

  static {
    NullNode.left = NullNode;
    NullNode.right = NullNode;
  }

  private final Node<T> root;
  private final Comparator<T> comparator;
  private final int size;

  public PersistentSet() {
    this(NullNode, new Comparator<T>() {
        public int compare(T a, T b) {
          return ((Comparable<T>) a).compareTo(b);
        }
      }, 0);
  }

  public PersistentSet(Comparator<T> comparator) {
    this(NullNode, comparator, 0);
  }

  private PersistentSet(Node<T> root, Comparator<T> comparator, int size) {
    this.root = root;
    this.comparator = comparator;
    this.size = size;
  }

  public Comparator<T> comparator() {
    return comparator;
  }

  public PersistentSet<T> add(T value) {
    return add(value, false);
  }

  public int size() {
    return size;
  }

  public PersistentSet<T> add(T value, boolean replaceExisting) {
    Path<T> p = find(value);
    if (! p.fresh) {
      if (replaceExisting) {
        return p.replaceWith(value);
      } else {
        return this;
      }
    }

    return add(p);
  }

  private PersistentSet<T> add(Path<T> p) {
    if (! p.fresh) throw new IllegalArgumentException();

    Node<T> new_ = p.node;
    Node<T> newRoot = p.root.root;
    Cell<Node<T>> ancestors = p.ancestors;

    // rebalance
    new_.red = true;
    while (ancestors != null && ancestors.value.red) {
      if (ancestors.value == ancestors.next.value.left) {
        if (ancestors.next.value.right.red) {
          ancestors.value.red = false;
          ancestors.next.value.right = new Node(ancestors.next.value.right);
          ancestors.next.value.right.red = false;
          ancestors.next.value.red = true;
          new_ = ancestors.next.value;
          ancestors = ancestors.next.next;
        } else {
          if (new_ == ancestors.value.right) {
            new_ = ancestors.value;
            ancestors = ancestors.next;

            Node<T> n = leftRotate(new_);
            if (ancestors.value.right == new_) {
              ancestors.value.right = n;
            } else {
              ancestors.value.left = n;
            }
            ancestors = new Cell(n, ancestors);
          }
          ancestors.value.red = false;
          ancestors.next.value.red = true;

          Node<T> n = rightRotate(ancestors.next.value);
          if (ancestors.next.next == null) {
            newRoot = n;
          } else if (ancestors.next.next.value.right == ancestors.next.value) {
            ancestors.next.next.value.right = n;
          } else {
            ancestors.next.next.value.left = n;
          }
          // done
        }
      } else {
        if (ancestors.next.value.left.red) {
          ancestors.value.red = false;
          ancestors.next.value.left = new Node(ancestors.next.value.left);
          ancestors.next.value.left.red = false;
          ancestors.next.value.red = true;
          new_ = ancestors.next.value;
          ancestors = ancestors.next.next;
        } else {
          if (new_ == ancestors.value.left) {
            new_ = ancestors.value;
            ancestors = ancestors.next;

            Node<T> n = rightRotate(new_);
            if (ancestors.value.right == new_) {
              ancestors.value.right = n;
            } else {
              ancestors.value.left = n;
            }
            ancestors = new Cell(n, ancestors);
          }
          ancestors.value.red = false;
          ancestors.next.value.red = true;

          Node<T> n = leftRotate(ancestors.next.value);
          if (ancestors.next.next == null) {
            newRoot = n;
          } else if (ancestors.next.next.value.right == ancestors.next.value) {
            ancestors.next.next.value.right = n;
          } else {
            ancestors.next.next.value.left = n;
          }
          // done
        }
      }
    }

    newRoot.red = false;

    return new PersistentSet(newRoot, comparator, size + 1);
  }

  private static <T> Node<T> leftRotate(Node<T> n) {
    Node<T> child = new Node(n.right);
    n.right = child.left;
    child.left = n;
    return child;
  }

  private static <T> Node<T> rightRotate(Node<T> n) {
    Node<T> child = new Node(n.left);
    n.left = child.right;
    child.right = n;
    return child;
  }

  public PersistentSet<T> remove(T value) {
    Path<T> p = find(value);
    if (! p.fresh) {
      return remove(p);
    }

    return this;
  }

  private PersistentSet<T> remove(Path<T> p) {
    Node<T> new_ = p.node;
    Node<T> newRoot = p.root.root;
    Cell<Node<T>> ancestors = p.ancestors;

    Node<T> dead;
    if (new_.left == NullNode || new_.right == NullNode) {
      dead = new_;
    } else {
      Cell<Node<T>> path = successor(new_, ancestors);
      dead = path.value;
      ancestors = path.next;
    }
    
    Node<T> child;
    if (dead.left != NullNode) {
      child = dead.left;
    } else {
      child = dead.right;
    }

    if (ancestors == null) {
      child.red = false;
      return new PersistentSet(child, comparator, 1);
    } else if (dead == ancestors.value.left) {
      ancestors.value.left = child;
    } else {
      ancestors.value.right = child;
    }

    if (dead != new_) {
      new_.value = dead.value;
    }

    if (! dead.red) {
      // rebalance
      while (ancestors != null && ! child.red) {
        if (child == ancestors.value.left) {
          Node<T> sibling = ancestors.value.right
            = new Node(ancestors.value.right);
          if (sibling.red) {
            sibling.red = false;
            ancestors.value.red = true;
            
            Node<T> n = leftRotate(ancestors.value);
            if (ancestors.next == null) {
              newRoot = n;
            } else if (ancestors.next.value.right == ancestors.value) {
              ancestors.next.value.right = n;
            } else {
              ancestors.next.value.left = n;
            }
            ancestors.next = new Cell(n, ancestors.next);

            sibling = ancestors.value.right;
          }

          if (! (sibling.left.red || sibling.right.red)) {
            sibling.red = true;
            child = ancestors.value;
            ancestors = ancestors.next;
          } else {
            if (! sibling.right.red) {
              sibling.left = new Node(sibling.left);
              sibling.left.red = false;

              sibling.red = true;
              sibling = ancestors.value.right = rightRotate(sibling);
            }

            sibling.red = ancestors.value.red;
            ancestors.value.red = false;

            sibling.right = new Node(sibling.right);
            sibling.right.red = false;
            
            Node<T> n = leftRotate(ancestors.value);
            if (ancestors.next == null) {
              newRoot = n;
            } else if (ancestors.next.value.right == ancestors.value) {
              ancestors.next.value.right = n;
            } else {
              ancestors.next.value.left = n;
            }

            child = newRoot;
            ancestors = null;
          }
        } else {
          Node<T> sibling = ancestors.value.left
            = new Node(ancestors.value.left);
          if (sibling.red) {
            sibling.red = false;
            ancestors.value.red = true;
            
            Node<T> n = rightRotate(ancestors.value);
            if (ancestors.next == null) {
              newRoot = n;
            } else if (ancestors.next.value.left == ancestors.value) {
              ancestors.next.value.left = n;
            } else {
              ancestors.next.value.right = n;
            }
            ancestors.next = new Cell(n, ancestors.next);

            sibling = ancestors.value.left;
          }

          if (! (sibling.right.red || sibling.left.red)) {
            sibling.red = true;
            child = ancestors.value;
            ancestors = ancestors.next;
          } else {
            if (! sibling.left.red) {
              sibling.right = new Node(sibling.right);
              sibling.right.red = false;

              sibling.red = true;
              sibling = ancestors.value.left = leftRotate(sibling);
            }

            sibling.red = ancestors.value.red;
            ancestors.value.red = false;

            sibling.left = new Node(sibling.left);
            sibling.left.red = false;
            
            Node<T> n = rightRotate(ancestors.value);
            if (ancestors.next == null) {
              newRoot = n;
            } else if (ancestors.next.value.left == ancestors.value) {
              ancestors.next.value.left = n;
            } else {
              ancestors.next.value.right = n;
            }

            child = newRoot;
            ancestors = null;
          }
        }
      }

      child.red = false;
    }

    return new PersistentSet(newRoot, comparator, size - 1);
  }

  private static <T> Cell<Node<T>> minimum(Node<T> n,
                                           Cell<Node<T>> ancestors)
  {
    while (n.left != NullNode) {
      n.left = new Node(n.left);
      ancestors = new Cell(n, ancestors);
      n = n.left;
    }

    return new Cell(n, ancestors);
  }

  private static <T> Cell<Node<T>> successor(Node<T> n,
                                             Cell<Node<T>> ancestors)
  {
    if (n.right != NullNode) {
      n.right = new Node(n.right);
      return minimum(n.right, new Cell(n, ancestors));
    }

    while (ancestors != null && n == ancestors.value.right) {
      n = ancestors.value;
      ancestors = ancestors.next;
    }

    return ancestors;
  }

  public Path<T> find(T value) {
    Node<T> newRoot = new Node(root);
    Cell<Node<T>> ancestors = null;

    Node<T> old = root;
    Node<T> new_ = newRoot;
    while (old != NullNode) {
      ancestors = new Cell(new_, ancestors);

      int difference = comparator.compare(value, old.value);
      if (difference < 0) {
        old = old.left;
        new_ = new_.left = new Node(old);
      } else if (difference > 0) {
        old = old.right;
        new_ = new_.right = new Node(old);
      } else {
        return new Path(false, new_,
                        new PersistentSet(newRoot, comparator, size),
                        ancestors.next);
      }
    }

    new_.value = value;
    return new Path(true, new_,
                    new PersistentSet(newRoot, comparator, size),
                    ancestors);
  }

  public Path<T> first() {
    if (root == NullNode) return null;

    Node<T> newRoot = new Node(root);
    Cell<Node<T>> ancestors = null;

    Node<T> old = root;
    Node<T> new_ = newRoot;
    while (old.left != NullNode) {
      ancestors = new Cell(new_, ancestors);

      old = old.left;
      new_ = new_.left = new Node(old);
    }

    return new Path(true, new_,
                    new PersistentSet(newRoot, comparator, size),
                    ancestors);
  }

  public Path<T> last() {
    if (root == NullNode) return null;

    Node<T> newRoot = new Node(root);
    Cell<Node<T>> ancestors = null;

    Node<T> old = root;
    Node<T> new_ = newRoot;
    while (old.right != NullNode) {
      ancestors = new Cell(new_, ancestors);

      old = old.right;
      new_ = new_.right = new Node(old);
    }

    return new Path(true, new_,
                    new PersistentSet(newRoot, comparator, size),
                    ancestors);
  }

  public java.util.Iterator<T> iterator() {
    return new Iterator(first());
  }

  private Path<T> successor(Path<T> p) {
    Cell<Node<T>> s = successor(p.node, p.ancestors);
    if (s == null) {
      return null;
    } else {
      return new Path(false, s.value, p.root, s.next);
    }
  }

//   public void dump(java.io.PrintStream out) {
//     dump(root, out, 0);
//   }

//   private static void indent(java.io.PrintStream out, int level) {
//     for (int i = 0; i < level; ++i) out.print("  ");
//   }

//   private static <T> void dump(Node<T> n, java.io.PrintStream out, int level) {
//     indent(out, level);
//     out.print(n == NullNode ? null : n.value);
//     out.println(n == NullNode ? "" : n.red ? " (red)" : " (black)");
//     if (n.left != NullNode || n.right != NullNode) {
//       dump(n.left, out, level + 1);
//       dump(n.right, out, level + 1);
//     }
//   }

//   private static int[] randomSet(java.util.Random r, int size) {
//     int[] data = new int[size];
//     for (int i = size - 1; i >= 0; --i) {
//       data[i] = i + 1;
//     }

//     for (int i = size - 1; i >= 0; --i) {
//       int n = r.nextInt(size);
//       int tmp = data[i];
//       data[i] = data[n];
//       data[n] = tmp;
//     }

//     return data;
//   }

//   public static void main(String[] args) {
//     java.util.Random r = new java.util.Random(Integer.parseInt(args[0]));
//     int size = 18;
//     PersistentSet<Integer>[] sets = new PersistentSet[size];
//     PersistentSet<Integer> s = new PersistentSet();

//     int[] data = randomSet(r, size);

//     for (int i = 0; i < size; ++i) {
//       System.out.println("-- add " + data[i] + " -- ");
//       sets[i] = s = s.add(data[i]);
//       dump(s.root, System.out, 0);
//     }

//     System.out.println("\npersistence:\n");
//     for (int i = 0; i < size; ++i) {
//       dump(sets[i].root, System.out, 0);
//       System.out.println("--");
//     }

//     data = randomSet(r, size);

//     System.out.println("\nremoval:\n");
//     for (int i = 0; i < size; ++i) {
//       System.out.println("-- remove " + data[i] + " -- ");
//       sets[i] = s = s.remove(data[i]);
//       dump(s.root, System.out, 0);
//     }

//     System.out.println("\npersistence:\n");
//     for (int i = 0; i < size; ++i) {
//       dump(sets[i].root, System.out, 0);
//       System.out.println("--");
//     }
//   }

  private static class Node <T> {
    public T value;
    public Node left;
    public Node right;
    public boolean red;
    
    public Node(Node<T> basis) {
      if (basis != null) {
        value = basis.value;
        left = basis.left;
        right = basis.right;
        red = basis.red;
      }
    }
  }

  public static class Path <T> {
    private final boolean fresh;
    private final Node<T> node;
    private final PersistentSet<T> root;
    private final Cell<Node<T>> ancestors;
    
    public Path(boolean fresh, Node<T> node, PersistentSet<T> root,
                Cell<Node<T>> ancestors)
    {
      this.fresh = fresh;
      this.node = node;
      this.root = root;
      this.ancestors = ancestors;
    }

    public T value() {
      return node.value;
    }

    public boolean fresh() {
      return fresh;
    }

    public PersistentSet<T> root() {
      return root;
    }

    public Path<T> successor() {
      return root.successor(this);
    }

    public PersistentSet<T> remove() {
      return root.remove(this);
    }

    public PersistentSet<T> add() {
      if (! fresh) throw new IllegalStateException();

      return root.add(this);
    }

    public PersistentSet<T> replaceWith(T value) {
      if (fresh) throw new IllegalStateException();
      if (root.comparator.compare(node.value, value) != 0)
        throw new IllegalArgumentException();

      node.value = value;
      return root;
    }
  }
  
  public class Iterator <T> implements java.util.Iterator <T> {
    private PersistentSet.Path<T> path;

    private Iterator(PersistentSet.Path<T> path) {
      this.path = path;
    }

    private Iterator(Iterator<T> start) {
      path = start.path;
    }

    public boolean hasNext() {
      return path != null;
    }

    public T next() {
      PersistentSet.Path<T> p = path;
      path = path.successor();
      return p.value();
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
