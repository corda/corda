package java.security;

/**
 * No real access control is implemented here.
 * 
 * @author zsombor
 *
 */
public class AccessController {

  public static Object doPrivileged (PrivilegedAction action) {
    return action.run();
  }

}
