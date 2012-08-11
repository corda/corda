package sun.misc;

public final class Unsafe {
  private void Unsafe() { }

  private static final Unsafe Instance = new Unsafe();

  public static Unsafe getUnsafe() {
    return Instance;
  }

  public native long allocateMemory(long bytes);

  public native void setMemory
    (Object base, long offset, long count, byte value);

  public native void freeMemory(long address);

  public native byte getByte(long address);

  public native void putByte(long address, byte x);

  public native short getShort(long address);

  public native void putShort(long address, short x);

  public native char getChar(long address);

  public native void putChar(long address, char x);

  public native int getInt(long address);

  public native void putInt(long address, int x);

  public native long getLong(long address);

  public native void putLong(long address, long x);

  public native float getFloat(long address);

  public native void putFloat(long address, float x);

  public native double getDouble(long address);

  public native void putDouble(long address, double x);

  public native long getAddress(long address);

  public native void putAddress(long address, long x);

  public native int arrayBaseOffset(Class arrayClass);

  public native void copyMemory(Object srcBase, long srcOffset,
                                Object destBase, long destOffset,
                                long count);

  public void copyMemory(long src, long dst, long count) {
    copyMemory(null, src, null, dst, count);
  }
}
