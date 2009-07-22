public class Finalizers {
  private static boolean finalized = false;

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  protected void finalize() {
    finalized = true;
  }

  public static void main(String[] args) {
    new Finalizers();

    expect(! finalized);
    
    System.gc();

    expect(finalized);

    new Finalizers2();
    
    finalized = false;

    expect(! finalized);
    
    System.gc();

    expect(finalized);
  }

  private static class Finalizers2 extends Finalizers { }

}
