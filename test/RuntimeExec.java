import java.lang.Runtime;
import java.lang.Process;

public class RuntimeExec {
  public static void main(String[] args) throws java.io.IOException, java.lang.InterruptedException {
    Runtime runtime = Runtime.getRuntime();

    System.out.println("Executing internet explorer");
    String ieStr = "\"c:\\program files\\internet explorer\\iexplore.exe\" http://www.google.com"; 
    Process ie = runtime.exec(ieStr);

    System.out.println("Executing firefox");
    String[] firefox = new String[2];
    firefox[0] = "c:\\program files\\mozilla firefox\\firefox.exe";
    firefox[1] = "http://www.google.com";
    Process ff = runtime.exec(firefox);

    boolean ffSuccess = false;
    boolean ieSuccess = false;
    while(!(ieSuccess && ffSuccess)){
      if(!ffSuccess){
        try{
          System.out.println("Firefox exit value: " + ff.exitValue());
          ffSuccess = true;
        } catch(IllegalThreadStateException e) {}
      }
      if(!ieSuccess){
        try{
          System.out.println("Internet Explorer exit value: " + ie.exitValue());
          ieSuccess = true;
        } catch(IllegalThreadStateException e) {}
      }
    }

    System.out.println("Executing and waiting for charmap");
    String charmapStr = "c:\\windows\\system32\\charmap.exe";
    Process cm = runtime.exec(charmapStr);
    System.out.println("Charmap exit value: " + cm.waitFor());
  }
}
