import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapTest {
  private static final int ThreadCount = 4;
  private static final int IterationCount = 100;
  private static final int Range = 10;
  private static final int CommonBase = -Range;

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Throwable {
    final ConcurrentMap<Integer, Object> map = new ConcurrentHashMap();
    final int[] counter = new int[1];
    final int[] step = new int[1];
    final Throwable[] exception = new Throwable[1];

    synchronized (map) {
      for (int i = 0; i < ThreadCount; ++i) {
        final int index = i;
        new Thread() {
          public void run() {
            try {
              synchronized (map) {
                ++ counter[0];
                map.notifyAll();
                while (exception[0] == null && step[0] == 0) {
                  map.wait();
                }
              }

              for (int i = 0; i < IterationCount; ++i) {
                populateCommon(map);
                populate(map, index * Range);
              }

              synchronized (map) {
                -- counter[0];
                map.notifyAll();
                while (exception[0] == null && step[0] == 1) {
                  map.wait();
                }
              }

              for (int i = 0; i < IterationCount; ++i) {
                populate(map, index * Range);
                depopulate(map, index * Range);
              }

              synchronized (map) {
                ++ counter[0];
                map.notifyAll();
              }
            } catch (Throwable e) {
              synchronized (map) {
                exception[0] = e;
                map.notifyAll();
              }
              e.printStackTrace();
            }
          }
        }.start();
      }

      try {
        while (exception[0] == null && counter[0] < ThreadCount) {
          map.wait();
        }

        step[0] = 1;
        map.notifyAll();

        while (exception[0] == null && counter[0] > 0) {
          map.wait();
        }

        if (map.size() != ThreadCount * Range) {
          System.err.println
            ("expected " + (ThreadCount * Range) + " got " + map.size());
        }
        expect(map.size() == ThreadCount * Range);
        for (int i = CommonBase, j = CommonBase + Range; i < j; ++i) {
          expect(! map.containsKey(i));
        }
      
        step[0] = 2;
        map.notifyAll();

        while (exception[0] == null && counter[0] < ThreadCount) {
          map.wait();
        }

        expect(map.isEmpty());
        expect(exception[0] == null);
      } catch (Throwable e) {
        exception[0] = e;
        throw e;
      } finally {
        map.notifyAll();
      }
    }
  }

  private static void populateCommon(ConcurrentMap<Integer, Object> map) {
    Object value = new Object();
    for (int i = CommonBase, j = CommonBase + Range; i < j; ++i) {
      map.remove(i);
      map.put(i, value);
      map.remove(i);
    }
  }

  private static void populate(ConcurrentMap<Integer, Object> map, int base) {
    for (int i = base, j = base + Range; i < j; ++i) {
      map.remove(i);
      Object value = new Object();
      expect(map.put(i, value) == null);
      expect(map.containsKey(i));
      expect(map.get(i).equals(value));
      expect(map.putIfAbsent(i, new Object()) == value);
      expect(map.get(i).equals(value));
      expect(! map.remove(i, new Object()));
      expect(map.remove(i, value));
      expect(map.replace(i, value) == null);
      expect(! map.containsKey(i));
      expect(map.get(i) == null);
      expect(map.putIfAbsent(i, value) == null);
      expect(map.containsKey(i));
      expect(map.get(i) == value);
      Object newValue = new Object();
      expect(map.replace(i, newValue) == value);
      expect(map.get(i) == newValue);

      boolean found = false;
      for (Iterator<Map.Entry<Integer, Object>> it = map.entrySet().iterator();
           it.hasNext();)
      {
        Map.Entry<Integer, Object> e = it.next();
        if (e.getKey() == i) {
          expect(! found);
          expect(e.getValue() == newValue);
          found = true;
          it.remove();
        }
      }

      expect(found);
      expect(! map.containsKey(i));
      expect(map.putIfAbsent(i, value) == null);
      expect(map.containsKey(i));
      expect(map.get(i) == value);
    }
  }

  private static void depopulate(ConcurrentMap<Integer, Object> map, int base)
  {
    for (int i = base, j = base + Range; i < j; ++i) {
      expect(map.containsKey(i));
      expect(map.remove(i) != null);
    }
  }
}
