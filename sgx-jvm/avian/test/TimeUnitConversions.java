import java.util.concurrent.TimeUnit;

public class TimeUnitConversions {
  private static void expect(long v1, long v2) {
    if (v1 != v2) {
      throw new RuntimeException(v1 + " != " + v2);
    }
  }
  
  private static void toNanoConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.NANOSECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.MICROSECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.MINUTES), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.NANOSECONDS.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toMicroConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.MICROSECONDS.convert(1000, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.MICROSECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.MILLISECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.SECONDS), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.MINUTES), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.MICROSECONDS.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toMilliConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.MILLISECONDS.convert(1000L * 1000, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.MILLISECONDS.convert(1000, TimeUnit.MICROSECONDS), expectedValue);
    expect(TimeUnit.MILLISECONDS.convert(1, TimeUnit.MILLISECONDS), expectedValue);
    expectedValue *= 1000;
    expect(TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toSecondConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.SECONDS.convert(1000L * 1000 * 1000, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.SECONDS.convert(1000L * 1000, TimeUnit.MICROSECONDS), expectedValue);
    expect(TimeUnit.SECONDS.convert(1000, TimeUnit.MILLISECONDS), expectedValue);
    expect(TimeUnit.SECONDS.convert(1, TimeUnit.SECONDS), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.SECONDS.convert(1, TimeUnit.MINUTES), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.SECONDS.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.SECONDS.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toMinuteConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.MINUTES.convert(1000L * 1000 * 1000 * 60, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.MINUTES.convert(1000L * 1000 * 60, TimeUnit.MICROSECONDS), expectedValue);
    expect(TimeUnit.MINUTES.convert(1000L * 60, TimeUnit.MILLISECONDS), expectedValue);
    expect(TimeUnit.MINUTES.convert(60, TimeUnit.SECONDS), expectedValue);
    expect(TimeUnit.MINUTES.convert(1, TimeUnit.MINUTES), expectedValue);
    expectedValue *= 60;
    expect(TimeUnit.MINUTES.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.MINUTES.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toHourConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.HOURS.convert(1000L * 1000 * 1000 * 60 * 60, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.HOURS.convert(1000L * 1000 * 60 * 60, TimeUnit.MICROSECONDS), expectedValue);
    expect(TimeUnit.HOURS.convert(1000L * 60 * 60, TimeUnit.MILLISECONDS), expectedValue);
    expect(TimeUnit.HOURS.convert(60L * 60, TimeUnit.SECONDS), expectedValue);
    expect(TimeUnit.HOURS.convert(60, TimeUnit.MINUTES), expectedValue);
    expect(TimeUnit.HOURS.convert(1, TimeUnit.HOURS), expectedValue);
    expectedValue *= 24;
    expect(TimeUnit.HOURS.convert(1, TimeUnit.DAYS), expectedValue);
  }
  
  private static void toDayConversionTest() {
    long expectedValue = 1;
    expect(TimeUnit.DAYS.convert(1000L * 1000 * 1000 * 60 * 60 * 24, TimeUnit.NANOSECONDS), expectedValue);
    expect(TimeUnit.DAYS.convert(1000L * 1000 * 60 * 60 * 24, TimeUnit.MICROSECONDS), expectedValue);
    expect(TimeUnit.DAYS.convert(1000L * 60 * 60 * 24, TimeUnit.MILLISECONDS), expectedValue);
    expect(TimeUnit.DAYS.convert(60L * 60 * 24, TimeUnit.SECONDS), expectedValue);
    expect(TimeUnit.DAYS.convert(60L * 24, TimeUnit.MINUTES), expectedValue);
    expect(TimeUnit.DAYS.convert(24, TimeUnit.HOURS), expectedValue);
    expect(TimeUnit.DAYS.convert(1, TimeUnit.DAYS), expectedValue);
  }

  public static void main(String[] args) {
    toNanoConversionTest();
    toMicroConversionTest();
    toMilliConversionTest();
    toSecondConversionTest();
    toMinuteConversionTest();
    toHourConversionTest();
    toDayConversionTest();
  }
}
