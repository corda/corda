package sandbox.sun.misc;

import sandbox.java.lang.Enum;

@SuppressWarnings("unused")
public class SharedSecrets extends sandbox.java.lang.Object {
    private static final JavaLangAccess javaLangAccess = new JavaLangAccessImpl();

    private static class JavaLangAccessImpl implements JavaLangAccess {
        @SuppressWarnings("unchecked")
        @Override
        public <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> enumClass) {
            return (E[]) sandbox.java.lang.DJVM.getEnumConstantsShared(enumClass);
        }
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess;
    }
}
