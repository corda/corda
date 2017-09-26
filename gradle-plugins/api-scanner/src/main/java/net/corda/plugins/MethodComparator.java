package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;

import java.lang.reflect.Field;
import java.util.Comparator;

public class MethodComparator implements Comparator<MethodInfo> {

    private static final Field DESCRIPTOR_FIELD;
    static {
        try {
            DESCRIPTOR_FIELD = MethodInfo.class.getDeclaredField("typeDescriptor");
            DESCRIPTOR_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static String getDescriptor(MethodInfo method) {
        try {
            return (String) DESCRIPTOR_FIELD.get(method);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public int compare(MethodInfo method1, MethodInfo method2) {
        int cmp = method1.getMethodName().compareTo(method2.getMethodName());
        if (cmp == 0) {
            cmp = method1.getAccessFlags() - method2.getAccessFlags();
            if (cmp == 0) {
                String descriptor1 = getDescriptor(method1);
                String descriptor2 = getDescriptor(method2);
                cmp = descriptor1.compareTo(descriptor2);
                if (cmp == 0) {
                    cmp = new ListComparator<String>().compare(method1.getAnnotationNames(), method2.getAnnotationNames());
                }
            }
        }
        return cmp;
    }
}
