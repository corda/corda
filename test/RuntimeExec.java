import java.lang.Runtime;

public class RuntimeExec {
  public static void main(String[] args) throws java.io.IOException {
    System.out.println("Executing internet explorer");
    Runtime.getRuntime().exec("\"c:\\program files\\internet explorer\\iexplore.exe\" http://www.google.com");
    System.out.println("Executing firefox");
    String[] firefox = new String[2];
    firefox[0] = "c:\\program files\\mozilla firefox\\firefox.exe";
    firefox[1] = "http://www.google.com";
    Runtime.getRuntime().exec(firefox);
  }
}
