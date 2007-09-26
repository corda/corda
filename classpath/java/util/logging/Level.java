package java.util.logging;

public class Level {
  public static final Level FINEST = new Level("FINEST", 300);
  public static final Level FINER = new Level("FINER", 400);
  public static final Level FINE = new Level("FINE", 500);
  public static final Level INFO = new Level("INFO", 800);
  public static final Level WARNING = new Level("WARNING", 900);
  public static final Level SEVERE = new Level("SEVERE", 1000);

  private final int value;
  private final String name;

  private Level(String name, int value) {
    this.name = name;
    this.value = value;
  }

  public int intValue() {
    return value;
  }

  public String getName() {
    return name;
  }

  public String toString() {
    return name;
  }
}
