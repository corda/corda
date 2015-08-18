public class InvokeDynamic {
  private final int foo;

  private InvokeDynamic(int foo) {
    this.foo = foo;
  }
  
  private interface Operation {
    int operate(int a, int b);
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }
  
  public static void main(String[] args) {
    int c = 4;
    Operation op = (a, b) -> a + b - c;
    expect(op.operate(2, 3) == (2 + 3) - 4);

    for (int i = 0; i < 4; ++i) {
      new InvokeDynamic(i).test();
    }
  }

  private void test() {
    int c = 2;
    Operation op = (a, b) -> ((a + b) * c) - foo;
    expect(op.operate(2, 3) == ((2 + 3) * 2) - foo);
  }
}
