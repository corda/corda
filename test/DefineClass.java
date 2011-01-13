import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;

public class DefineClass {
  private static File findHello(File directory) {
    File[] files = directory.listFiles();
    for (File file: directory.listFiles()) {
      if (file.isFile()) {
        if (file.getName().equals("Hello.class")) {
          return file;
        }
      } else if (file.isDirectory()) {
        File result = findHello(file);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private static byte[] read(File file) throws IOException {
    byte[] bytes = new byte[(int) file.length()];
    FileInputStream in = new FileInputStream(file);
    try {
      if (in.read(bytes) != (int) file.length()) {
        throw new RuntimeException();
      }
      return bytes;
    } finally {
      in.close();
    }
  }

  public static void main(String[] args) throws Exception {
    byte[] bytes = read(findHello(new File(System.getProperty("user.dir"))));
    Class c = new MyClassLoader(DefineClass.class.getClassLoader()).defineClass
      ("Hello", bytes);
    c.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class defineClass(String name, byte[] bytes) {
      return super.defineClass(name, bytes, 0, bytes.length);
    }
  }
}
