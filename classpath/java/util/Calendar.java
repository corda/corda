package java.util;

public abstract class Calendar {
  public static final int AM = 0;
  public static final int AM_PM = 9;
  public static final int DAY_OF_MONTH = 5;
  public static final int HOUR = 10;
  public static final int HOUR_OF_DAY = 11;
  public static final int MINUTE = 12;
  public static final int MONTH = 2;
  public static final int PM = 1;
  public static final int SECOND = 13;
  public static final int YEAR = 1;

  public static final int FIELD_COUNT = 17;

  protected long time;
  protected boolean isTimeSet;
  protected int[] fields = new int[FIELD_COUNT];
  protected boolean areFieldsSet;
  protected boolean[] isSet = new boolean[FIELD_COUNT];

  protected Calendar() { }
 
  public static Calendar getInstance() {
    return new MyCalendar(System.currentTimeMillis());
  }

  public int get(int field) {
    return fields[field];
  }

  public void set(int field, int value) {
    fields[field] = value;
  }

  public void setTime(Date date) {
    time = date.getTime();
  }

  public abstract void roll(int field, boolean up);

  public void roll(int field, int amount) {
    boolean up = amount >= 0;
    if (! up) {
      amount = - amount;
    }
    for (int i = 0; i < amount; ++i) {
      roll(field, up);
    } 
  }

  public abstract int getMinimum(int field);

  public abstract int getMaximum(int field);
 
  public abstract int getActualMinimum(int field);

  public abstract int getActualMaximum(int field);

  private static class MyCalendar extends Calendar {
    private static final long MILLIS_PER_DAY = 86400000;
    private static final int MILLIS_PER_HOUR = 3600000;
    private static final int MILLIS_PER_MINUTE = 60000;
    private static final int MILLIS_PER_SECOND = 1000;

    private static final int EPOCH_YEAR = 1970;
    private static final int[][] DAYS_IN_MONTH = new int[][] {
      { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 },
      { 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 }
    };

    public MyCalendar(long time) {
      this.time = time;
      this.isTimeSet = true;
      parseIntoFields(time);
    }

    public void setTime(Date date) {
      super.setTime(date);
      parseIntoFields(this.time);
    }

    private static boolean isLeapYear(int year) {
      return (year%4 == 0) && (year%100 != 0) || (year%400 == 0);
    }
    
    private static int daysInYear(int year) {
      return isLeapYear(year) ? 366 : 365;
    }
    
    private void parseIntoFields(long timeInMillis) {
      long days = timeInMillis / MILLIS_PER_DAY;
      int year = EPOCH_YEAR;
      while (days >= daysInYear(year)) {
        days -= daysInYear(year++);
      }
      int month=0;
      int leapIndex = isLeapYear(year) ? 1 : 0;
      while (days >= DAYS_IN_MONTH[leapIndex][month]) {
        days -= DAYS_IN_MONTH[leapIndex][month++];
      }
      days++;
      int remainder = (int)(timeInMillis % MILLIS_PER_DAY);
      int hour = remainder / MILLIS_PER_HOUR;
      remainder = remainder % MILLIS_PER_HOUR;
      int minute = remainder / MILLIS_PER_MINUTE;
      remainder = remainder / MILLIS_PER_MINUTE;
      int second = remainder / MILLIS_PER_SECOND;
      fields[YEAR] = year;
      fields[MONTH] = month;
      fields[DAY_OF_MONTH] = (int)days;
      fields[HOUR_OF_DAY] = hour;
      fields[MINUTE] = minute;
      fields[SECOND] = second;
    }
    
    public void roll(int field, boolean up) {
      // todo
    }

    public int getMinimum(int field) {
      // todo
      return 0;
    }

    public int getMaximum(int field) {
      // todo
      return 0;
    }
 
    public int getActualMinimum(int field) {
      // todo
      return 0;
    }

    public int getActualMaximum(int field) {
      // todo
      return 0;
    }
  }
}
