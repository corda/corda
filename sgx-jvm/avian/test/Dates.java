import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Dates {
  private final static long EPOCH = 1234567890;
  private final static String TEXT = "2009-02-13T23:31:30";

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    format.setTimeZone(TimeZone.getTimeZone("GMT"));
    Date date = format.parse("1970-01-01T00:00:00");
    expect(0 == date.getTime());

    date = new Date(EPOCH * 1000l);
    String actual = format.format(date, new StringBuffer(), new FieldPosition(0)).toString();
    expect(TEXT.equals(actual));

    date = format.parse(TEXT);
    expect(EPOCH == date.getTime() / 1000l);
  }
}
