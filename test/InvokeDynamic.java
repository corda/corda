public class InvokeDynamic {

  public static void main(String[] args) {
    Runnable r = () -> System.out.println("success");
    r.run();
  }
}
