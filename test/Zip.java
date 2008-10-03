import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public class Zip {
  
  public static void main(String[] args) throws Exception {
    ZipFile file = new ZipFile("build/classpath.jar");

    byte[] buffer = new byte[4096];
    for (Enumeration<ZipEntry> e = file.entries(); e.hasMoreElements();) {
      ZipEntry entry = e.nextElement();
      InputStream in = file.getInputStream(entry);
      try {
        int size = 0;
        int c; while ((c = in.read(buffer)) != -1) size += c;
        System.out.println
          (entry.getName() + " " + entry.getCompressedSize() + " " + size);
      } finally {
        in.read();
      }
    }
  }

}
