package java.net;

import java.io.IOException;

public abstract class URLStreamHandler {
  protected void parseURL(URL url, String s, int start, int end) {
    String protocol = s.substring(0, start - 1);
    s = s.substring(start, end);

    String host = null;
    int port = -1;
    if (s.startsWith("//")) {
      s = s.substring(2);
      int colon = s.indexOf(':');
      int slash = s.indexOf('/');
      if (slash < 0) {
        if (colon < 0) {
          host = s;
        } else {
          host = s.substring(0, colon);
          port = Integer.parseInt(s.substring(colon + 1));
        }
        s = "";
      } else {
        if (colon < 0 || colon > slash) {
          host = s.substring(0, slash);
        } else {
          host = s.substring(0, colon);
          port = Integer.parseInt(s.substring(colon + 1), slash);
        }
        s = s.substring(slash + 1);
      }
    }

    String file = null;
    if (s.length() > 0) {
      file = s;
    }

    url.set(protocol, host, port, file, null);
  }

  private static boolean equals(String a, String b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  protected boolean equals(URL a, URL b) {
    return equals(a.getHost(), b.getHost())
      && (a.getPort() == b.getPort())
      && equals(a.getFile(), b.getFile());
  }

  protected String toExternalForm(URL url) {
    StringBuilder sb = new StringBuilder();
    sb.append(url.getProtocol()).append("://");
    if (url.getHost() != null) {
      sb.append(url.getHost());
      if (url.getPort() >= 0) {
        sb.append(":").append(url.getPort());
      }
    }
    if (url.getFile() != null) {
      sb.append("/").append(url.getFile());
    }
    return sb.toString();
  }

  protected abstract URLConnection openConnection(URL url) throws IOException;
}
