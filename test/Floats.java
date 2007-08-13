public class Floats {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    expect(0.5d * 0.5d == 0.25d);
    expect(0.5f * 0.5f == 0.25f);

    expect(0.5d * 0.5d < 0.5d);
    expect(0.5f * 0.5f < 0.5f);

    expect(0.5d * 0.5d > 0.1d);
    expect(0.5f * 0.5f > 0.1f);

    expect(0.5d / 0.5d == 1.0d);

    expect(0.5d - 0.5d == 0.0d);
  }
}
