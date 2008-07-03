package java.lang;

public interface CharSequence {
	public char charAt(int index);
	
	int length();
	
	CharSequence subSequence(int start, int end);
	
	String toString();
}
