/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public interface DataOutput {
	void write(byte[] b) throws IOException;
	void write(byte[] b, int off, int len) throws IOException;
	void write(int b) throws IOException;
	void writeBoolean(boolean v) throws IOException;
	void writeByte(int v) throws IOException;
	void writeBytes(String s) throws IOException;
	void writeChar(int v) throws IOException;
	void writeChars(String s) throws IOException;
	void writeDouble(double v) throws IOException;
	void writeFloat(float v) throws IOException;
	void writeInt(int v) throws IOException;
	void writeLong(long v) throws IOException;
	void writeShort(int v) throws IOException;
	void writeUTF(String s) throws IOException;
}
