/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

public final class ByteOrder {
    public static final ByteOrder BIG_ENDIAN = new ByteOrder("BIG_ENDIAN");
    public static final ByteOrder LITTLE_ENDIAN = new ByteOrder("LITTLE_ENDIAN");
    
    private static final ByteOrder NATIVE;
    private static native boolean isNativeBigEndian();

    static {
        if (isNativeBigEndian())
            NATIVE = BIG_ENDIAN;
        else
            NATIVE = LITTLE_ENDIAN;
    }

    private String name;

    private ByteOrder(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static ByteOrder nativeOrder() {
        return NATIVE;
    }
}
