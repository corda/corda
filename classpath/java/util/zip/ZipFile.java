package java.util.zip;

import java.io.RandomAccessFile;
import java.io.InputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

public class ZipFile {
  private final RandomAccessFile file;
  private final Window window;
  private final Map<String,Integer> index = new HashMap();

  public ZipFile(String name) throws IOException {
    file = new RandomAccessFile(name, "r");
    window = new Window(file, 4096);
    
    int fileLength = (int) file.length();
    int pointer = fileLength - 22;
    byte[] magic = new byte[] { 0x50, 0x4B, 0x05, 0x06 };
    while (pointer > 0) {
      if (equal(window.data, window.seek(pointer, magic.length),
                magic, 0, magic.length))
      {
        pointer = directoryOffset(window, pointer);

        magic = new byte[] { 0x50, 0x4B, 0x01, 0x02 };
        while (pointer < fileLength) {
          if (equal(window.data, window.seek(pointer, magic.length),
                    magic, 0, magic.length))
          {
            index.put(entryName(window, pointer), pointer);
            pointer = entryEnd(window, pointer);
          } else {
            pointer = fileLength;
          }
        }
        pointer = 0;
      } else {
        -- pointer;
      }
    }
  }

  public int size() {
    return index.size();
  }

  public Enumeration<ZipEntry> entries() {
    return new MyEnumeration(window, index.values().iterator());
  }

  public ZipEntry getEntry(String name) {
    Integer pointer = index.get(name);
    return (pointer == null ? null : new MyZipEntry(window, pointer));
  }

  public InputStream getInputStream(ZipEntry entry) throws IOException {
    int pointer = ((MyZipEntry) entry).pointer;
    int method = compressionMethod(window, pointer);
    int size = compressedSize(window, pointer);
    InputStream in = new MyInputStream(file, fileData(window, pointer), size);

    final int Stored = 0;
    final int Deflated = 8;

    switch (method) {
    case Stored:
      return in;

    case Deflated:
      return new InflaterInputStream(in, new Inflater(true));

    default:
      throw new IOException();
    }
  }

  private static boolean equal(byte[] a, int aOffset, byte[] b, int bOffset,
                               int size)
  {
    for (int i = 0; i < size; ++i) {
      if (a[aOffset + i] != b[bOffset + i]) return false;
    }
    return true;
  }

  private static int get2(Window w, int p) throws IOException {
    int offset = w.seek(p, 2);
    return
      ((w.data[offset + 1] & 0xFF) <<  8) |
      ((w.data[offset    ] & 0xFF)      );
  }

  private static int get4(Window w, int p) throws IOException {
    int offset = w.seek(p, 4);
    return
      ((w.data[offset + 3] & 0xFF) << 24) |
      ((w.data[offset + 2] & 0xFF) << 16) |
      ((w.data[offset + 1] & 0xFF) <<  8) |
      ((w.data[offset    ] & 0xFF)      );
  }

  private static int directoryOffset(Window w, int p) throws IOException {
    return get4(w, p + 16);
  }

  private static int entryNameLength(Window w, int p) throws IOException {
    return get2(w, p + 28);
  }

  private static String entryName(Window w, int p) throws IOException {
    int length = entryNameLength(w, p);
    return new String(w.data, w.seek(p + 46, length), length);
  }

  private static int compressionMethod(Window w, int p) throws IOException {
    return get2(w, p + 10);
  }

  private static int compressedSize(Window w, int p) throws IOException {
    return get4(w, p + 20);
  }

  private static int fileNameLength(Window w, int p) throws IOException {
    return get2(w, p + 28);
  }

  private static int extraFieldLength(Window w, int p) throws IOException {
    return get2(w, p + 30);
  }

  private static int commentFieldLength(Window w, int p) throws IOException {
    return get2(w, p + 32);
  }

  private static int entryEnd(Window w, int p) throws IOException {
    final int HeaderSize = 46;
    return p + HeaderSize
      + fileNameLength(w, p)
      + extraFieldLength(w, p)
      + commentFieldLength(w, p);
  }

  private static int fileData(Window w, int p) throws IOException {
    int localHeader = localHeader(w, p);
    final int LocalHeaderSize = 30;
    return localHeader
      + LocalHeaderSize
      + localFileNameLength(w, localHeader)
      + localExtraFieldLength(w, localHeader);
  }

  private static int localHeader(Window w, int p) throws IOException {
    return get4(w, p + 42);
  }

  private static int localFileNameLength(Window w, int p) throws IOException {
    return get2(w, p + 26);
  }

  private static int localExtraFieldLength(Window w, int p)
    throws IOException
  {
    return get2(w, p + 28);
  }

  private static class Window {
    private final RandomAccessFile file;
    public final byte[] data;
    public int start;
    public int length;
    
    public Window(RandomAccessFile file, int size) {
      this.file = file;
      data = new byte[size];
    }

    public int seek(int start, int length) throws IOException {
      int fileLength = (int) file.length();

      if (length > data.length) {
        throw new IllegalArgumentException
          ("length " + length + " greater than buffer length " + data.length);
      }

      if (start < 0) {
        throw new IllegalArgumentException("negative start " + start);
      }

      if (start + length > fileLength) {
        throw new IllegalArgumentException
          ("end " + (start + length) + " greater than file length " +
           fileLength);
      }

      if (start < this.start || start + length > this.start + this.length) {
        this.length = Math.min(data.length, fileLength);
        this.start = start - ((this.length - length) / 2);
        if (this.start < 0) {
          this.start = 0;
        } else if (this.start + this.length > fileLength) {
          this.start = fileLength - this.length;
        }
        file.seek(this.start);
//         System.out.println("start " + start + " length " + length + " this start " + this.start + " this.length " + this.length + " file length " + fileLength);
        file.readFully(data, 0, this.length);
      }

      return start - this.start;
    }
  }

  private static class MyZipEntry extends ZipEntry {
    public final Window window;
    public final int pointer;

    public MyZipEntry(Window window, int pointer) {
      this.window = window;
      this.pointer = pointer;
    }

    public String getName() {
      try {
        return entryName(window, pointer);
      } catch (IOException e) {
        return null;
      }
    }

    public int getCompressedSize() {
      try {
        return compressedSize(window, pointer);
      } catch (IOException e) {
        return 0;
      }
    }
  }

  private static class MyEnumeration implements Enumeration<ZipEntry> {
    private final Window window;
    private final Iterator<Integer> iterator;

    public MyEnumeration(Window window, Iterator<Integer> iterator) {
      this.window = window;
      this.iterator = iterator;
    }

    public boolean hasMoreElements() {
      return iterator.hasNext();
    }

    public ZipEntry nextElement() {
      return new MyZipEntry(window, iterator.next());
    }
  }

  private static class MyInputStream extends InputStream {
    private RandomAccessFile file;
    private int offset;
    private int length;

    public MyInputStream(RandomAccessFile file, int start, int length) {
      this.file = file;
      this.offset = start;
      this.length = length;
    }

    public int read() throws IOException {
      byte[] b = new byte[1];
      int c = read(b);
      return (c == -1 ? -1 : b[0] & 0xFF);
    }

    public int read(byte[] b, int offset, int length) throws IOException {
      if (this.length == 0) return -1;

      if (length > this.length) length = this.length;

      file.seek(this.offset);
      file.readFully(b, offset, length);

      this.offset += length;
      this.length -= length;

      return length;
    }

    public void close() throws IOException {
      file = null;
    }
  }
}
