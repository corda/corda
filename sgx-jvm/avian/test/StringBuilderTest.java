
public class StringBuilderTest {
  private static final int iterations = 1000;
    
  public static void main(String[] args) {
    verifyAppendStrLength();
    verifyAppendCharLength();
    verifySubstring();
  }
  
  private static void verify(String srcStr, int iterations, String result) {
    int expectedLength = srcStr.length() * iterations;
    if (result.length() != expectedLength) {
      throw new IllegalStateException("Incorrect length: " + result.length() + " vs " + expectedLength);
    }
  }
  
  private static void verify(String expected, String actual) {
    if (! expected.equals(actual)) {
      throw new IllegalStateException("Strings don't match, expected: " + expected + ", actual: " + actual);
    }
  }
  
  private static void verifyAppendStrLength() {
    String fooStr = "foobar";
    
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < iterations; i++) {
      sb.append(fooStr);
    }
    String result = sb.toString();
    
    verify(fooStr, iterations, result);
  }
  
  private static void verifyAppendCharLength() {
    int iterations = 5000;
    String fooStr = "foobar";
    char[] fooChars = new char[fooStr.length()];
    fooStr.getChars(0, fooStr.length(), fooChars, 0);
    
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < iterations; i++) {
      for (int j = 0; j < fooChars.length; j++) {
        sb.append(fooChars[j]);
      }
    }
    String result = sb.toString();
    
    verify(fooStr, iterations, result);
  }
  
  private static void verifySubstring() {
    String fooStr = "foobar";
    StringBuilder sb = new StringBuilder();
    sb.append(fooStr);
    sb.append(fooStr);
    
    String beginingSubString = sb.substring(0, fooStr.length());
    verify(fooStr, beginingSubString);
    
    String endSubString = sb.substring(fooStr.length());
    verify(fooStr, endSubString);
  }
}
