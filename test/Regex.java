import regex.Matcher;
import regex.Pattern;

public class Regex {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static Matcher getMatcher(String regex, String string) {
    return Pattern.compile(regex).matcher(string);
  }

  private static void expectMatch(String regex, String string) {
    expect(getMatcher(regex, string).matches());
  }

  private static void expectNoMatch(String regex, String string) {
    expect(!getMatcher(regex, string).matches());
  }

  public static void main(String[] args) {
    expectMatch("a(bb)?a", "abba");
    expectNoMatch("a(bb)?a", "abbba");
    expectNoMatch("a(bb)?a", "abbaa");
  }
}
