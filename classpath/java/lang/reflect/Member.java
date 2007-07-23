package java.lang.reflect;

public interface Member {
  public static final int PUBLIC = 0;
  public static final int DECLARED = 1;

  public Class getDeclaringClass();

  public int getModifiers();

  public String getName();
}
