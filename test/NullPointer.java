public class NullPointer {
  private int x;
  private Object y;

  public static void main(String[] args) {
    // invokeinterface
    try {
      ((Runnable) null).run();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // invokevirtual
    try {
      ((Object) null).toString();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // arraylength
    try {
      int a = ((byte[]) null).length;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // iaload
    try {
      int a = ((byte[]) null)[42];
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // aaload
    try {
      Object a = ((Object[]) null)[42];
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // getfield (int)
    try {
      int a = ((NullPointer) null).x;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // getfield (Object)
    try {
      Object a = ((NullPointer) null).y;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // iastore
    try {
      ((byte[]) null)[42] = 42;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // aastore
    try {
      ((Object[]) null)[42] = null;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // putfield (int)
    try {
      ((NullPointer) null).x = 42;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // putfield (Object)
    try {
      ((NullPointer) null).y = null;
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    // monitorenter
    try {
      synchronized ((Object) null) {
        int a = 42;
      }
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
  }
}
