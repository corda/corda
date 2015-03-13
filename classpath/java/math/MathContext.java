/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.math;

import java.io.Serializable;

public final class MathContext implements Serializable {

  public static final MathContext DECIMAL32  = new MathContext( 7, RoundingMode.HALF_EVEN);
  public static final MathContext DECIMAL64  = new MathContext(16, RoundingMode.HALF_EVEN);
  public static final MathContext DECIMAL128 = new MathContext(34, RoundingMode.HALF_EVEN);
  public static final MathContext UNLIMITED  = new MathContext(0,  RoundingMode.HALF_UP);

  private int precision;
  private RoundingMode roundingMode;

  public MathContext(int precision, RoundingMode roundingMode) {
    if (precision < 0)
        throw new IllegalArgumentException();
    if (roundingMode == null)
        throw new NullPointerException();
    this.precision    = precision;
    this.roundingMode = roundingMode;
  }

  public MathContext(int precision) {
    this(precision, RoundingMode.HALF_UP);
  }

  public int getPrecision() {
    return precision;
  }

  public RoundingMode getRoundingMode() {
    return roundingMode;
  }

  @Override
  public boolean equals(Object that) {
    return
        (that instanceof MathContext) &&
        (precision    == ((MathContext) that).getPrecision()) &&
        (roundingMode == ((MathContext) that).getRoundingMode());
  }

  @Override
  public int hashCode() {
    return
        roundingMode.ordinal() |
        (precision << 4);
  }

  private final static String precisionString    = "precision=";
  private final static String roundingModeString = " roundingMode=";

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(48);
    sb.append(precisionString).append(precision);
    sb.append(roundingModeString).append(roundingMode);
    return sb.toString();
  }
}
