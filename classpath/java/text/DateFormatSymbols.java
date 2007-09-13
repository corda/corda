package java.text;

public class DateFormatSymbols {
  private String[] ampm = new String[] { "AM", "PM" };

  public String[] getAmPmStrings() {
    return ampm;
  }

  public void setAmPmStrings(String[] v) {
    ampm = v;
  }
}
