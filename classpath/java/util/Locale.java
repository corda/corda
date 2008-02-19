/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

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
