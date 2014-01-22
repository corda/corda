package java.lang;

/**
 * Used by <code>String</code> to signal that a given index is either less than
 * or greater than the allowed range.
 */
public class StringIndexOutOfBoundsException extends IndexOutOfBoundsException {
  private static final long serialVersionUID = -6762910422159637258L;

  public StringIndexOutOfBoundsException(int index) {
    super("String index out of range: "+index);
  }

  public StringIndexOutOfBoundsException(String message) {
	super(message);
  }

  public StringIndexOutOfBoundsException() {}
}
