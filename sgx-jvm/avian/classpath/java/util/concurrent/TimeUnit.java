/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public enum TimeUnit {
  NANOSECONDS {
    @Override
    public long toNanos(long d) {
      return d;
    }
    
    @Override
    public long toMicros(long d) {
      return d / NANOSECONDS_PER_MICROSECOND;
    }
    
    @Override
    public long toMillis(long d) {
      return d / NANOSECONDS_PER_MILLISECOND;
    }
    
    @Override
    public long toSeconds(long d) {
      return d / NANOSECONDS_PER_SECOND;
    }
    
    @Override
    public long toMinutes(long d) {
      return d / NANOSECONDS_PER_MINUTE;
    }
    
    @Override
    public long toHours(long d) {
      return d / NANOSECONDS_PER_HOUR;
    }
    
    @Override
    public long toDays(long d) {
      return d / NANOSECONDS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toNanos(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return (int) (d - (m * NANOSECONDS_PER_MILLISECOND));
    }
  },
  MICROSECONDS {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_MICROSECOND);
    }
    
    @Override
    public long toMicros(long d) {
      return d;
    }
    
    @Override
    public long toMillis(long d) {
      return d / MICROSECONDS_PER_MILLISECOND;
    }
    
    @Override
    public long toSeconds(long d) {
      return d / MICROSECONDS_PER_SECOND;
    }
    
    @Override
    public long toMinutes(long d) {
      return d / MICROSECONDS_PER_MINUTE;
    }
    
    @Override
    public long toHours(long d) {
      return d / MICROSECONDS_PER_HOUR;
    }
    
    @Override
    public long toDays(long d) {
      return d / MICROSECONDS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toMicros(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return (int) ((d * NANOSECONDS_PER_MICROSECOND) - (m * NANOSECONDS_PER_MILLISECOND));
    }
  },
  MILLISECONDS {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_MILLISECOND);
    }
    
    @Override
    public long toMicros(long d) {
      return scale(d, MICROSECONDS_PER_MILLISECOND);
    }
    
    @Override
    public long toMillis(long d) {
      return d;
    }
    
    @Override
    public long toSeconds(long d) {
      return d / MILLISECONDS_PER_SECOND;
    }
    
    @Override
    public long toMinutes(long d) {
      return d / MILLISECONDS_PER_MINUTE;
    }
    
    @Override
    public long toHours(long d) {
      return d / MILLISECONDS_PER_HOUR;
    }
    
    @Override
    public long toDays(long d) {
      return d / MILLISECONDS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toMillis(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return 0;
    }
  },
  SECONDS {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_SECOND);
    }
    
    @Override
    public long toMicros(long d) {
      return scale(d, MICROSECONDS_PER_SECOND);
    }
    
    @Override
    public long toMillis(long d) {
      return scale(d, MILLISECONDS_PER_SECOND);
    }
    
    @Override
    public long toSeconds(long d) {
      return d;
    }
    
    @Override
    public long toMinutes(long d) {
      return d / SECONDS_PER_MINUTE;
    }
    
    @Override
    public long toHours(long d) {
      return d / SECONDS_PER_HOUR;
    }
    
    @Override
    public long toDays(long d) {
      return d / SECONDS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toSeconds(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return 0;
    }
  },
  MINUTES {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_MINUTE);
    }
    
    @Override
    public long toMicros(long d) {
      return scale(d, MICROSECONDS_PER_MINUTE);
    }
    
    @Override
    public long toMillis(long d) {
      return scale(d, MILLISECONDS_PER_MINUTE);
    }
    
    @Override
    public long toSeconds(long d) {
      return scale(d, SECONDS_PER_MINUTE);
    }
    
    @Override
    public long toMinutes(long d) {
      return d;
    }
    
    @Override
    public long toHours(long d) {
      return d / MINUETS_PER_HOUR;
    }
    
    @Override
    public long toDays(long d) {
      return d / MINUETS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toMinutes(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return 0;
    }
  },
  HOURS {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_HOUR);
    }
    
    @Override
    public long toMicros(long d) {
      return scale(d, MICROSECONDS_PER_HOUR);
    }
    
    @Override
    public long toMillis(long d) {
      return scale(d, MILLISECONDS_PER_HOUR);
    }
    
    @Override
    public long toSeconds(long d) {
      return scale(d, SECONDS_PER_HOUR);
    }
    
    @Override
    public long toMinutes(long d) {
      return scale(d, MINUETS_PER_HOUR);
    }
    
    @Override
    public long toHours(long d) {
      return d;
    }
    
    @Override
    public long toDays(long d) {
      return d / HOURS_PER_DAY;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toHours(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return 0;
    }
  },
  DAYS {
    @Override
    public long toNanos(long d) {
      return scale(d, NANOSECONDS_PER_DAY);
    }
    
    @Override
    public long toMicros(long d) {
      return scale(d, MICROSECONDS_PER_DAY);
    }
    
    @Override
    public long toMillis(long d) {
      return scale(d, MILLISECONDS_PER_DAY);
    }
    
    @Override
    public long toSeconds(long d) {
      return scale(d, SECONDS_PER_DAY);
    }
    
    @Override
    public long toMinutes(long d) {
      return scale(d, MINUETS_PER_DAY);
    }
    
    @Override
    public long toHours(long d) {
      return scale(d, HOURS_PER_DAY);
    }
    
    @Override
    public long toDays(long d) {
      return d;
    }
    
    @Override
    public long convert(long d, TimeUnit u) {
      return u.toDays(d);
    }
    
    @Override
    int excessNanos(long d, long m) {
      return 0;
    }
  };
  
  private static final long NANOSECONDS_PER_MICROSECOND = 1000L;
  private static final long MICROSECONDS_PER_MILLISECOND = 1000L;
  private static final long MILLISECONDS_PER_SECOND = 1000L;
  private static final long SECONDS_PER_MINUTE = 60;
  private static final long MINUETS_PER_HOUR = 60;
  private static final long HOURS_PER_DAY = 24;
  
  private static final long NANOSECONDS_PER_MILLISECOND = NANOSECONDS_PER_MICROSECOND * MICROSECONDS_PER_MILLISECOND;
  private static final long NANOSECONDS_PER_SECOND = NANOSECONDS_PER_MILLISECOND * MILLISECONDS_PER_SECOND;
  private static final long NANOSECONDS_PER_MINUTE = NANOSECONDS_PER_SECOND * SECONDS_PER_MINUTE;
  private static final long NANOSECONDS_PER_HOUR = NANOSECONDS_PER_MINUTE * MINUETS_PER_HOUR;
  private static final long NANOSECONDS_PER_DAY = NANOSECONDS_PER_HOUR * HOURS_PER_DAY;
  
  private static final long MICROSECONDS_PER_SECOND = MICROSECONDS_PER_MILLISECOND * MILLISECONDS_PER_SECOND;
  private static final long MICROSECONDS_PER_MINUTE = MICROSECONDS_PER_SECOND * SECONDS_PER_MINUTE;
  private static final long MICROSECONDS_PER_HOUR = MICROSECONDS_PER_MINUTE * MINUETS_PER_HOUR;
  private static final long MICROSECONDS_PER_DAY = MICROSECONDS_PER_HOUR * HOURS_PER_DAY;
  
  private static final long MILLISECONDS_PER_MINUTE = MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE;
  private static final long MILLISECONDS_PER_HOUR = MILLISECONDS_PER_MINUTE * MINUETS_PER_HOUR;
  private static final long MILLISECONDS_PER_DAY = MILLISECONDS_PER_HOUR * HOURS_PER_DAY;
  
  private static final long SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUETS_PER_HOUR;
  private static final long SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;
  
  private static final long MINUETS_PER_DAY = MINUETS_PER_HOUR * HOURS_PER_DAY;
  
  private static long scale(long value, long conversion) {
    long result = value * conversion;
    if (value > 0 && result < value) {
      return Long.MAX_VALUE;
    } else if (value < 0 && result > value) { 
      return Long.MIN_VALUE;
    } else {
      return result;
    }
  }
  
  public abstract long convert(long sourceDuration, TimeUnit sourceUnit);
  
  public abstract long toNanos(long duration);
  
  public abstract long toMicros(long duration);
  
  public abstract long toMillis(long duration);
  
  public abstract long toSeconds(long duration);
  
  public abstract long toMinutes(long duration);
  
  public abstract long toHours(long duration);
  
  public abstract long toDays(long duration);
  
  abstract int excessNanos(long d, long m);
  
  public void timedWait(Object obj, long timeout) throws InterruptedException {
    if (timeout > 0) {
      long ms = toMillis(timeout);
      int ns = excessNanos(timeout, ms);
      obj.wait(ms, ns);
    }
  }
}
