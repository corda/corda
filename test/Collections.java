import java.util.Collection;
import java.util.Map;

public class Collections {
  public static void main(String[] args) {
    testValues();
  }
  
  @SuppressWarnings("rawtypes")
  private static void testValues() {
    Map testMap = java.util.Collections.unmodifiableMap(java.util.Collections.emptyMap());
    Collection values = testMap.values();
    
    if (values == null) {
      throw new NullPointerException();
    }
    
    try {
      values.clear();
      
      throw new IllegalStateException("Object should be immutable, exception should have thrown");
    } catch (Exception e) {
      // expected
    }
  }
}
