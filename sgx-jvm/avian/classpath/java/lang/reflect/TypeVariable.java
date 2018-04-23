package java.lang.reflect;

public interface TypeVariable<D extends GenericDeclaration> extends Type {
  Type[] getBounds();
  D getGenericDeclaration();
  String getName();
}
