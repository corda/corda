package java.util;

public class Locale {
  public static final Locale ENGLISH = new Locale("en");

  private final String language;
  private final String country;
  private final String variant;

  public Locale(String language, String country, String variant) {
    this.language = language;
    this.country = country;
    this.variant = variant;
  }

  public Locale(String language, String country) {
    this(language, country, null);
  }

  public Locale(String language) {
    this(language, null);
  }

  public String getLanguage() {
    return language;
  }

  public String getCountry() {
    return country;
  }

  public String getVariant() {
    return variant;
  }

  public static Locale getDefault() {
    return ENGLISH;
  }
}
