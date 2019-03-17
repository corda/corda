package sandbox.sun.misc;

import sandbox.java.lang.Enum;

@SuppressWarnings("unused")
public interface JavaLangAccess {

    <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> enumClass);

}
