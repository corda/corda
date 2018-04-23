/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

public class Cell <T> {
  public T value;
  public Cell<T> next;
  
  public Cell(T value, Cell<T> next) {
    this.value = value;
    this.next = next;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (Cell c = this; c != null; c = c.next) {
      sb.append(value);
      if (c.next != null) {
        sb.append(" ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static <Car> Cell<Car> cons(Car car, Cell<Car> cdr) {
    return new Cell(car, cdr);
  }

  public static <T> boolean equal(T a, T b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }
  
  public static <Car> boolean equal(Cell<Car> a, Cell<Car> b) {
    while (a != null) {
      if (b == null || (! equal(a.value, b.value))) {
        return false;
      }
      a = a.next;
      b = b.next;
    }

    return b == null;
  }
}
