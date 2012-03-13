import java.io.File;

public class Files {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }
  
  private static void isAbsoluteTest(boolean absolutePath) {
    File file = new File("test.txt");
    if (absolutePath) {
      file = file.getAbsoluteFile();
    }
    
    boolean isAbsolute = file.isAbsolute();
    
    if (absolutePath) {
      expect(isAbsolute);
    } else {
      expect(!isAbsolute);
    }
    
  }

  private static void setExecutableTestWithPermissions(boolean executable)
    throws Exception
  {
    File file = File.createTempFile("avian.", null);
    file.setExecutable(executable);
    if (executable) {
      expect(file.canExecute());
    } else {
      // Commented out because this will fail on Windows - both on Avian and on OpenJDK
      // The implementation for Windows considers canExecute() to be the same as canRead()
      // expect(!file.canExecute());
    }
  }
  
  public static void main(String[] args) throws Exception {
    isAbsoluteTest(true);
    isAbsoluteTest(false);
    setExecutableTestWithPermissions(true);
    setExecutableTestWithPermissions(false);
  
    { File f = new File("test.txt");
      f.createNewFile();
      expect(! f.createNewFile());
      f.delete();
    }
  }

}
