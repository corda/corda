package avian.testing.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface TestComplex {
  public Test[] arrayValue();
  public Class classValue();
  public String stringValue();
  public char charValue();
  public double doubleValue();
}

