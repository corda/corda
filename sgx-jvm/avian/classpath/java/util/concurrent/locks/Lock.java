/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

public interface Lock {
  public void lock();
  public void lockInterruptibly() throws InterruptedException;
  public Condition newCondition();
  public boolean tryLock();
  public boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
  public void unlock();
}
