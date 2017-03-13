/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public interface DataInput {
	boolean readBoolean() throws IOException;
	byte readByte() throws IOException;
	char readChar() throws IOException;
	double readDouble() throws IOException;
	float readFloat() throws IOException;
	void readFully(byte[] b) throws IOException;
	void readFully(byte[] b, int off, int len) throws IOException;
	int readInt() throws IOException;
	String readLine() throws IOException;
	long readLong() throws IOException;
	short readShort() throws IOException;
	int readUnsignedByte() throws IOException;
	int readUnsignedShort() throws IOException;
	String readUTF() throws IOException;
	int skipBytes(int n) throws IOException;
}
