/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.math;

public enum RoundingMode {

  UP         (0),
  DOWN       (1),
  CEILING    (2),
  FLOOR      (3),
  HALF_UP    (4),
  HALF_DOWN  (5),
  HALF_EVEN  (6),
  UNNECESSARY(7);

  RoundingMode(int rm) {
    roundingMode = rm;
  }

  private final int roundingMode;

  public static RoundingMode valueOf(int roundingMode) {
    final RoundingMode[] values = values();
    if (roundingMode < 0 || roundingMode >= values.length)
      throw new IllegalArgumentException();
    return values[roundingMode];
  }
}
