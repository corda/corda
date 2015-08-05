public class InvokeDynamic {
  private interface Operation {
    int operate(int a, int b);
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }
  
  public static void main(String[] args) {
    int c = 4;
    Operation op = (a, b) -> a + b - c;
    expect(op.operate(2, 3) == 1);
  }
}
