import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.io.IOException;

public class Sockets {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void testFailedBind() throws Exception {
    final String Hostname = "localhost";
    final int Port = 22046; // hopefully this port is unused
    final SocketAddress Address = new InetSocketAddress(Hostname, Port);
    final byte[] Message = "hello, world!".getBytes();

    SocketChannel out = SocketChannel.open();
    try {
      try {
        out.connect(Address);
        expect(false);
      } catch(IOException e) {
        // We're good.  This previously triggered a vm assert, rather than an exception
      }
    } finally {
      out.close();
    }
  }

  public static void main(String[] args) throws Exception {
    testFailedBind();
  }
}
