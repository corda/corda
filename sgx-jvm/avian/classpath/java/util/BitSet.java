/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

import java.io.Serializable;

/**
 * @author zsombor
 * 
 */
public class BitSet implements Serializable, Cloneable {

  final static int  BITS_PER_LONG       = 64;
  final static int  BITS_PER_LONG_SHIFT = 6;
  final static long MASK                = 0xFFFFFFFFFFFFFFFFL;

  private long[]    bits;

  private static int longPosition(int index) {
    return index >> BITS_PER_LONG_SHIFT;
  }

  private static long bitPosition(int index) {
    return 1L << (index % BITS_PER_LONG);
  }

  private static long getTrueMask(int fromIndex, int toIndex) {
    int currentRange = toIndex - fromIndex;
    return (MASK >>> (BITS_PER_LONG - currentRange)) << (fromIndex % BITS_PER_LONG);
  }

  public BitSet(int bitLength) {
    if (bitLength % BITS_PER_LONG == 0) {
      enlarge(longPosition(bitLength));
    } else {
      enlarge(longPosition(bitLength) + 1);
    }
  }

  public BitSet() {
    enlarge(1);
  }

  public void and(BitSet otherBits) {
    int min = Math.min(bits.length, otherBits.bits.length);
    for (int i = 0; i < min; i++) {
      bits[i] &= otherBits.bits[i];
    }
    for (int i = min; i < bits.length; i++) { 
      bits[i] = 0;
    }
  }

  public void andNot(BitSet otherBits) {
    int max = Math.max(bits.length, otherBits.bits.length);
    enlarge(max);
    int min = Math.min(bits.length, otherBits.bits.length);
    for (int i = 0; i < min; i++) {
      bits[i] &= ~otherBits.bits[i];
    }
  }

  public void or(BitSet otherBits) {
    int max = Math.max(bits.length, otherBits.bits.length);
    enlarge(max);
    int min = Math.min(bits.length, otherBits.bits.length);
    for (int i = 0; i < min; i++) {
      bits[i] |= otherBits.bits[i];
    }
  }

  public void xor(BitSet otherBits) {
    int max = Math.max(bits.length, otherBits.bits.length);
    enlarge(max);
    int min = Math.min(bits.length, otherBits.bits.length);
    for (int i = 0; i < min; i++) {
      bits[i] ^= otherBits.bits[i];
    }
  }

  private void enlarge(int newPartition) {
    if (bits == null || bits.length < (newPartition + 1)) {
      long[] newBits = new long[newPartition + 1];
      if (bits != null) {
        System.arraycopy(bits, 0, newBits, 0, bits.length);
      }
      bits = newBits;
    }
  }

  public boolean get(int index) {
    int pos = longPosition(index);
    if (pos < bits.length) {
      return (bits[pos] & bitPosition(index)) != 0;
    }
    return false;
  }

  public void flip(int index) {
    flip(index, index+1);
  }

  public void flip(int fromIndex, int toIndex) {
    if (fromIndex > toIndex || fromIndex < 0 || toIndex < 0) {
      throw new IndexOutOfBoundsException();
    } else if (fromIndex != toIndex) {
      MaskInfoIterator iter = new MaskInfoIterator(fromIndex, toIndex);
      enlarge(iter.getLastPartition());
      while (iter.hasNext()) {
        MaskInfo info = iter.next();
        bits[info.partitionIndex] ^= info.mask;
      }
    }
  }

  public void set(int index) {
    int pos = longPosition(index);
    enlarge(pos);
    bits[pos] |= bitPosition(index);
  }

  public void set(int start, int end) {
    MaskInfoIterator iter = new MaskInfoIterator(start, end);
    enlarge(iter.getLastPartition());
    while (iter.hasNext()) {
      MaskInfo info = iter.next();
      bits[info.partitionIndex] |= info.mask;
    }
  }

  public void clear(int index) {
    int pos = longPosition(index);
    if (pos < bits.length) {
      bits[pos] &= (MASK ^ bitPosition(index));
    }
  }

  public void clear(int start, int end) {
    MaskInfoIterator iter = new MaskInfoIterator(start, end);
    while (iter.hasNext()) {
      MaskInfo info = iter.next();
      bits[info.partitionIndex] &= (MASK ^ info.mask);
    }
  }

  public boolean isEmpty() {
    for (int i = 0; i < bits.length; i++) {
      if (bits[i] != 0) {
        return false;
      }
    }
    return true;
  }

  public boolean intersects(BitSet otherBits) {
    int max = Math.max(bits.length, otherBits.bits.length);
    for (int i = 0; i < max; i++) {
      if ((bits[i] & otherBits.bits[i]) != 0) {
        return true;
      }
    }
    return false;
  }

  public int length() {
    return bits.length << BITS_PER_LONG_SHIFT;
  }

  public int nextSetBit(int fromIndex) {
    return nextBit(fromIndex, false);
  }

  private int nextBit(int fromIndex, boolean bitClear) {
    int pos = longPosition(fromIndex);
    if (pos >= bits.length) {
      return -1;
    }
    int current = fromIndex;
    do {
      long currValue = bits[pos];
      if (currValue == 0) {
        pos++;
        current = pos << BITS_PER_LONG_SHIFT;
      } else {
        do {
          long bitPos = bitPosition(current);
          if (((currValue & bitPos) != 0) ^ bitClear) {
            return current;
          } else {
            current++;
          }
        } while (current % BITS_PER_LONG != 0);
      }
      pos++;
    } while (pos < bits.length);

    return -1;
  }

  public int nextClearBit(int fromIndex) {
    return nextBit(fromIndex, true);
  }

  public int cardinality() {
    int numSetBits = 0;
    for (int i = nextSetBit(0); i >= 0; i = nextSetBit(i+1)) {
      ++numSetBits;
    }
    
    return numSetBits;
  }

  private static class MaskInfoIterator implements Iterator<MaskInfo> {
    private int basePartition;
    private int numPartitionsToTraverse;
    private int currentPartitionOffset;
    private int toIndex;
    private int currentFirstIndex;

    public MaskInfoIterator(int fromIndex, int toIndex) {
      this.basePartition = longPosition(fromIndex);
      this.numPartitionsToTraverse = longPosition(toIndex - 1) - basePartition + 1;
      this.currentPartitionOffset = 0;
      this.toIndex = toIndex;
      this.currentFirstIndex = fromIndex;
    }

    public MaskInfo next() {
      int currentToIndex = Math.min(toIndex, (basePartition + currentPartitionOffset + 1) * BITS_PER_LONG);
      long mask = getTrueMask(currentFirstIndex, currentToIndex);
      MaskInfo info = new MaskInfo(mask, basePartition + currentPartitionOffset);
      currentFirstIndex = currentToIndex;
      currentPartitionOffset++;
      return info;
    }

    public boolean hasNext() {
      return currentPartitionOffset < numPartitionsToTraverse;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    public int getLastPartition() {
      return basePartition + numPartitionsToTraverse - 1;
    }
  }

  private static class MaskInfo {
    public long mask;
    public int partitionIndex;

    public MaskInfo(long mask, int partitionIndex) {
      this.mask = mask;
      this.partitionIndex = partitionIndex;
    }
  }
}
