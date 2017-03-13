import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

public class Datagrams {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static boolean equal(byte[] a, int aOffset, byte[] b, int bOffset,
                               int length)
  {
    for (int i = 0; i < length; ++i) {
      if (a[aOffset + i] != b[bOffset + i]) return false;
    }
    return true;
  }

  public static void main(String[] args) throws Exception {
    test(true);
    test(false);
  }

  private static void test(boolean send) throws Exception {
    final String Hostname = "localhost";
    final int InPort = 22043;
    final int OutPort = 22044;
    final SocketAddress InAddress = new InetSocketAddress(Hostname, InPort);
    final SocketAddress OutAddress = new InetSocketAddress(Hostname, OutPort);
    final byte[] Message = "hello, world!".getBytes();

    DatagramChannel out = DatagramChannel.open();
    try {
      out.configureBlocking(false);
      out.socket().bind(OutAddress);
      if (! send) out.connect(InAddress);
    
      DatagramChannel in = DatagramChannel.open();
      try {
        in.configureBlocking(false);
        in.socket().bind(InAddress);

        Selector selector = Selector.open();
        try {
          SelectionKey outKey = out.register
            (selector, SelectionKey.OP_WRITE, null);

          SelectionKey inKey = in.register
            (selector, SelectionKey.OP_READ, null);

          int state = 0;
          ByteBuffer inBuffer = ByteBuffer.allocate(Message.length);
          loop: while (true) {
            selector.select();

            switch (state) {
            case 0: {
              if (outKey.isWritable()) {
                if (send) {
                  out.send(ByteBuffer.wrap(Message), InAddress);
                } else {
                  out.write(ByteBuffer.wrap(Message));
                }
                state = 1;
              }
            } break;

            case 1: {
              if (inKey.isReadable()) {
                expect(in.receive(inBuffer).equals(OutAddress));
                if (! inBuffer.hasRemaining()) {
                  expect(equal(inBuffer.array(),
                               inBuffer.arrayOffset(),
                               Message,
                               0,
                               Message.length));
                  break loop;
                }
              }
            } break;

            default: throw new RuntimeException();
            }
          }
        } finally {
          selector.close();
        }
      } finally {
        in.close();
      }
    } finally {
      out.close();
    }
  }
}
