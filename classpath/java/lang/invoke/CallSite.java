package java.lang.invoke;

public class CallSite {
  private final MethodHandle target;

  CallSite(MethodHandle target) {
    this.target = target;
  }
}
