package java.text;

public class DateFormatSymbols {
  private String[] ampm = new String[] { "AM", "PM" };
  private String[] shortWeekdays = new String[] { "Sun", "Mon", "Tue",
						  "Wed", "Thu", "Fri", "Sat" };
  private String[] shortMonths = new String[] { "Jan", "Feb", "Mar", "Apr",
						"May", "Jun", "Jul", "Aug",
						"Sep", "Oct", "Nov", "Dec" };

  public String[] getAmPmStrings() {
    return ampm;
  }

  public void setAmPmStrings(String[] v) {
    ampm = v;
  }

  public String[] getShortWeekdays() {
    return shortWeekdays;
  }

  public String[] getShortMonths() {
    return shortMonths;
  }
}
