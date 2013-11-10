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

  private static void expectGroups(String regex, String string,
      String... groups) {
    Matcher matcher = getMatcher(regex, string);
    expect(matcher.matches());
    expect(matcher.groupCount() == groups.length);
    for (int i = 1; i <= groups.length; ++i) {
      if (groups[i - 1] == null) {
        expect(matcher.group(i) == null);
      } else {
        expect(groups[i - 1].equals(matcher.group(i)));
      }
    }
  }

  public static void main(String[] args) {
    expectMatch("a(bb)?a", "abba");
    expectNoMatch("a(bb)?a", "abbba");
    expectNoMatch("a(bb)?a", "abbaa");
    expectGroups("a(a*?)(a?)(a??)(a+)(a*)a", "aaaaaa", "", "a", "", "aaa", "");
    expectMatch("...", "abc");
    expectNoMatch(".", "\n");
    expectGroups("a(bb)*a", "abbbba", "bb");
    expectGroups("a(bb)?(bb)+a", "abba", null, "bb");
  }
}
