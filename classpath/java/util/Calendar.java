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
    public MyCalendar(long time) {
      this.time = time;
      this.isTimeSet = true;
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
