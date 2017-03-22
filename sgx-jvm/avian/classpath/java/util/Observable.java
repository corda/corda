/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class Observable {

  private List<Observer> observers = new ArrayList<Observer>();
  private boolean changed = false;

  public void addObserver(Observer o) {
    if(o == null) {
      throw new NullPointerException();
    }
    synchronized(this) {
      if(!observers.contains(o)) {
        observers.add(o);
      }
    }
  }

  public synchronized int countObservers() {
    return observers.size();
  }

  public void deleteObserver(Observer o) {
    if(o == null) {
      throw new NullPointerException();
    }
    synchronized(this) {
      observers.remove(o);
    }
  }

  public void notifyObservers() {
    notifyObservers(null);
  }

  public synchronized void notifyObservers(Object value) {
    Observer[] obsArray = null;
    synchronized(this) {
      if(hasChanged()) {
        clearChanged();
        obsArray = observers.toArray(new Observer[observers.size()]);
      }
    }
    if(obsArray != null) {
      for(Observer obs : obsArray) {
        obs.update(this, value);
      }
    }
  }

  public boolean hasChanged() {
    return changed;
  }

  protected void setChanged() {
    changed = true;
  }

  protected void clearChanged() {
    changed = false;
  }

}