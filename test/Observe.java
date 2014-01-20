import java.util.Observer;
import java.util.Observable;

public class Observe {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static class MyObservable extends Observable {
    private String value;

    public MyObservable(String value) {
      this.value = value;
    }

    public void set(String newValue) {
      if(!value.equals(newValue)) {
        value = newValue;
        setChanged();
        notifyObservers(value);
      }
    }
  }

  private static class MyObserver implements Observer {
    private int count = 0;
    private Observable expectedObs;
    private Object expectedValue = null;
    private boolean expected = false;

    public MyObserver(Observable expectedObs) {
      this.expectedObs = expectedObs;
    }

    public void update(Observable obs, Object value) {
      expect(expectedObs == expectedObs);
      expect(expected);
      expect(value == expectedValue);
      expectNothing();
    }

    public void noUpdate() {
      expect(!expected);
    }

    public void expect(Object value) {
      expected = true;
      expectedValue = value;
    }

    public void expectNothing() {
      expected = false;
    }
    
  }

  public static void main(String[] args) {
    MyObservable obs = new MyObservable("test");
    MyObserver o = new MyObserver(obs);
    MyObserver o2 = new MyObserver(obs);

    obs.set("a");

    obs.addObserver(o);
    o.expect("b");
    obs.set("b");
    o.noUpdate();

    obs.addObserver(o2);
    o.expect("c");
    o2.expect("c");
    obs.set("c");
    o.noUpdate();
    o2.noUpdate();

    obs.deleteObserver(o);
    o.expectNothing();
    o2.expect("d");
    obs.set("d");
    o2.noUpdate();

    obs.deleteObserver(o2);
    o.expectNothing();
    o2.expectNothing();
    obs.set("e");

  }
}