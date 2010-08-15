import java.io.FileOutputStream;
import java.io.IOException;


public class FileOutput {

  /**
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
      FileOutputStream f = new FileOutputStream("test.txt");
      f.write("Hello world!\n".getBytes());
      f.close();
      
      FileOutputStream f2 = new FileOutputStream("test.txt", true);
      f2.write("Hello world again!".getBytes());
      f2.close();
      

  }

}
