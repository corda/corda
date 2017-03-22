/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.math;

import java.io.Serializable;

public class BigInteger implements Serializable {

  private int   sign;
  private int[] value;

  private BigInteger(int sign, long value) {
    this.sign = sign;
    int upperBits = (int) (value >>> 32);
    if (upperBits == 0)
      // Array with one element
      this.value = new int[] { (int) value };
    else
      // Array with two elements
      this.value = new int[] { (int) value, upperBits };
  }

  public static final BigInteger ZERO = new BigInteger(0,  0);
  public static final BigInteger ONE  = new BigInteger(1,  1);
  public static final BigInteger TEN  = new BigInteger(1, 10);

  public static BigInteger valueOf(long num) {
    int signum = Long.signum(num);
    if (signum == 0)
      return BigInteger.ZERO;
    else if (signum > 0)
      return new BigInteger(signum, num);
    else
      return new BigInteger(signum, -num);   
  }
}
