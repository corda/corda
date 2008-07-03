/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

/**
 * @author zsombor
 * 
 */
public abstract class AbstractCollection<T> implements Collection<T> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#add(java.lang.Object)
	 */
	public boolean add(T element) {
		throw new UnsupportedOperationException("adding to "
				+ this.getClass().getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#addAll(java.util.Collection)
	 */
	public boolean addAll(Collection<? extends T> collection) {
		boolean result = false;
		for (T obj : collection) {
			result |= add(obj);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#clear()
	 */
	public void clear() {
		throw new UnsupportedOperationException("clear() in "
				+ this.getClass().getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#contains(java.lang.Object)
	 */
	public boolean contains(T element) {
		if (element != null) {
			for (Iterator<T> iter = iterator(); iter.hasNext();) {
				if (element.equals(iter.next())) {
					return true;
				}
			}
		} else {
			for (Iterator<T> iter = iterator(); iter.hasNext();) {
				if (iter.next()==null) {
					return true;
				}
			}
			
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#isEmpty()
	 */
	public boolean isEmpty() {
		return size() == 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#remove(java.lang.Object)
	 */
	public boolean remove(T element) {
		throw new UnsupportedOperationException("remove(T) in "
				+ this.getClass().getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#size()
	 */
	public abstract int size();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Collection#toArray(S[])
	 */
	public <S> S[] toArray(S[] array) {
		return Collections.toArray(this, array);
	}

	public abstract Iterator<T> iterator();

}
