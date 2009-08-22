public class Strings {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    expect(new String(new byte[] { 99, 111, 109, 46, 101, 99, 111, 118, 97,
                                   116, 101, 46, 110, 97, 116, 46, 98, 117,
                                   115, 46, 83, 121, 109, 98, 111, 108 })
      .equals("com.ecovate.nat.bus.Symbol"));
    
    final String months = "Jan\u00aeFeb\u00aeMar\u00ae";
    expect(months.split("\u00ae").length == 3);
    expect(months.replaceAll("\u00ae", ".").equals("Jan.Feb.Mar."));

    expect("foo_foofoo__foo".replaceAll("_", "__")
           .equals("foo__foofoo____foo"));

    expect("foo_foofoo__foo".replaceFirst("_", "__")
           .equals("foo__foofoo__foo"));

    expect("stereomime".matches("stereomime"));
    expect(! "stereomime".matches("stereomim"));
    expect(! "stereomime".matches("tereomime"));
    expect(! "stereomime".matches("sterEomime"));

    StringBuilder sb = new StringBuilder();
    sb.append('$');
    sb.append('2');
    expect(sb.substring(1).equals("2"));
  }
}
