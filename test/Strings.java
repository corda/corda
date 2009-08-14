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

    System.out.println(months.split("\u00ae")[0]);
    System.out.println(months.length());
    System.out.println(months);
    for (int i = 0; i < months.length(); ++i) {
      System.out.print(Integer.toHexString(months.charAt(i)) + " ");
    }
    System.out.println();

    expect(months.split("\u00ae").length == 3);

    StringBuilder sb = new StringBuilder();
    sb.append('$');
    sb.append('2');
    expect(sb.substring(1).equals("2"));
  }
}
