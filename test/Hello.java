public class Hello {
  public static void main(String[] args) {
    System.out.println("hello, world!");
    try {
      Runtime.class.getMethod("dumpHeap", String.class)
        .invoke(null, "/tmp/heap.bin");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
