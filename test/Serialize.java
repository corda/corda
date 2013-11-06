import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

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

  protected static void hexdump(byte[] a) {
    for (int i = 0; i < a.length; i++) {
      if ((i & 0xf) == 0) {
        System.err.println();
      }
      String hex = Integer.toHexString(a[i] & 0xff);
      System.err.print(" " + (hex.length() == 1 ? "0" : "") + hex);
    }
    System.err.println();
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
  }
}
