import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.TreeMap;

public class Serialize implements Serializable {
  public static final long serialVersionUID = 1l;
  public int dummy = 0x12345678;
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static void expectEqual(boolean a, boolean b) {
	  expect(a == b);
  }

  private static void expectEqual(int a, int b) {
	  expect(a == b);
  }

  private static void expectEqual(String a, String b) {
	  expect(a.equals(b));
  }

  private static String pad(long number, int length) {
    return pad(Long.toHexString(number), length, '0');
  }

  private static String pad(String s, int length, char padChar) {
    length -= s.length();
    if (length <= 0) {
      return s;
    }
    StringBuilder builder = new StringBuilder();
    while (length-- > 0) {
      builder.append(padChar);
    }
    return builder.append(s).toString();
  }

  protected static void hexdump(byte[] a) {
    StringBuilder builder = new StringBuilder();
    System.err.print(pad(0, 8) + " ");
    for (int i = 0; i < a.length; i++) {
      String hex = Integer.toHexString(a[i] & 0xff);
      System.err.print(" " + (hex.length() == 1 ? "0" : "") + hex);
      builder.append(a[i] < 0x20 || a[i] > 0x7f ? '.' : (char)a[i]);
      if ((i & 0xf) == 0x7) {
        System.err.print(" ");
      } else if ((i & 0xf) == 0xf) {
        System.err.println("  |" + builder + "|");
        builder.setLength(0);
        System.err.print(pad(i + 1, 8) + " ");
      }
    }
    for (int i = a.length & 0xf; i < 0x10; i++) {
      System.err.print("   ");
      if ((i & 0xf) == 0x7) {
        System.err.print(" ");
      }
    }
    System.err.println("  |" + builder + "|");
  }

  private static void expectEqual(byte[] a, int[] b) {
    expect(a.length == b.length);

    for (int i = 0; i < a.length; ++i) {
      expect(a[i] == (byte)b[i]);
    }
  }

  public static void main(String[] args) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream out2 = new ObjectOutputStream(out);
    out2.writeBoolean(true);
    out2.flush();
    out2.writeByte(17);
    out2.flush();
    out2.writeInt(0xcafebabe);
    out2.flush();
    out2.writeUTF("Max & Möritz");
    out2.flush();
    out2.writeChar('ɛ');
    out2.flush();
    out2.writeObject(new Serialize());
    out2.close();
    byte[] array = out.toByteArray();
    expectEqual(array, new int[] {
      // magic
      0xac, 0xed,
      // version
      0x00, 0x05,
      // blockdata, length
      0x77, 0x1,
      // true
      1,
      // blockdata, length
      0x77, 0x1,
      // (byte)17
      17,
      // blockdata, length
      0x77, 0x4,
      // 0xcafebabe
      0xca, 0xfe, 0xba, 0xbe,
      // blockdata, length
      0x77, 0xf,
      // "Max & Möritz"
      0x0, 0xd, 'M', 'a', 'x', ' ', '&', ' ', 'M', 0xc3, 0xb6, 'r', 'i', 't', 'z',
      // blockdata, length
      0x77, 0x2,
      // 'ö'
      0x02, 0x5b,
      // object
      0x73,
      // class desc, "Serialize"
      0x72, 0, 9, 'S', 'e', 'r', 'i', 'a', 'l', 'i', 'z', 'e',
      // serialVersionUID
      0, 0, 0, 0, 0, 0, 0, 1,
      // flags (SC_SERIALIZABLE)
      2,
      // field count
      0x0, 0x1,
      // int dummy
      'I', 0x0, 0x5, 'd', 'u', 'm', 'm', 'y',
      // class annotation
      0x78,
      // super class desc
      0x70,
      // classdata[]
      0x12, 0x34, 0x56, 0x78
    });
    ByteArrayInputStream in = new ByteArrayInputStream(array);
    ObjectInputStream in2 = new ObjectInputStream(in);
    expectEqual(true, in2.readBoolean());
    expectEqual(17, in2.readByte());
    expectEqual(0xcafebabe, in2.readInt());
    expectEqual("Max & Möritz", in2.readUTF());
    expectEqual('ɛ', in2.readChar());
    Serialize unserialized = (Serialize) in2.readObject();
    expectEqual(0x12345678, unserialized.dummy);
    in2.close();

    out.reset();
    out2 = new ObjectOutputStream(out);
    TreeMap map = new TreeMap();
    map.put("key", "value");
    out2.writeObject(map);
    out2.close();
    array = out.toByteArray();
    expectEqual(array, new int[] {
      // magic
      0xac, 0xed,
      // version
      0x00, 0x05,
      // object
      0x73,
      // class desc "java.util.TreeMap"
      0x72, 0, 17, 'j', 'a', 'v', 'a', '.', 'u', 't', 'i', 'l', '.',
      'T', 'r', 'e', 'e', 'M', 'a', 'p',
      // serial version UID: 0x0cc1f64e2d266ae6
      0x0c, 0xc1, 0xf6, 0x3e, 0x2d, 0x25, 0x6a, 0xe6,
      // flags: SC_SERIALIZABLE | SC_WRITE_METHOD
      0x03,
      // 1 field: comparator
      0, 1, 'L', 0, 10, 'c', 'o', 'm', 'p', 'a', 'r', 'a', 't', 'o', 'r',
      0x74, 0, 22, 'L', 'j', 'a', 'v', 'a', '/', 'u', 't', 'i', 'l', '/',
      'C', 'o', 'm', 'p', 'a', 'r', 'a', 't', 'o', 'r', ';',
      // class annotation
      0x78,
      // super class desc
      0x70,
      // classdata[]: NULL
      0x70,
      // custom TreeMap data writte by TreeMap#writeObject
      0x77, 4, 0x00 , 0x00, 0x00, 0x01, // (int)1 (== map.size())
      0x74, 0, 3, 'k', 'e', 'y', // "key"
      0x74, 0, 5, 'v', 'a', 'l', 'u', 'e', // "value"
      // end block data
      0x78
    });
    map.put("Hello", "ween");
    in = new ByteArrayInputStream(array);
    in2 = new ObjectInputStream(in);
    map = (TreeMap)in2.readObject();
    in2.close();
    expectEqual(1, map.size());
    expectEqual("value", (String)map.get("key"));
  }
}
