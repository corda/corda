package java.net;

public class InetSocketAddress {
  private final String host;
  private final int port;

  public InetSocketAddress(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public String getHostName() {
    return host;
  }

  public int getPort() {
    return port;
  }
}
