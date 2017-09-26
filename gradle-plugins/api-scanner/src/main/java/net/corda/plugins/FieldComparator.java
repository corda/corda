package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;

import java.lang.reflect.Field;
import java.util.Comparator;

public class FieldComparator implements Comparator<FieldInfo> {

    private static final Field DESCRIPTOR_FIELD;
    static {
        try {
            DESCRIPTOR_FIELD = FieldInfo.class.getDeclaredField("typeDescriptor");
            DESCRIPTOR_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static String getDescriptor(FieldInfo field) {
        try {
            return (String) DESCRIPTOR_FIELD.get(field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public int compare(FieldInfo field1, FieldInfo field2) {
        int cmp = field1.getFieldName().compareTo(field2.getFieldName());
        if (cmp == 0) {
            cmp = field1.getAccessFlags() - field2.getAccessFlags();
            if (cmp == 0) {
                String descriptor1 = getDescriptor(field1);
                String descriptor2 = getDescriptor(field2);
                cmp = descriptor1.compareTo(descriptor2);
                if (cmp == 0) {
                    cmp = new ListComparator<String>().compare(field1.getAnnotationNames(), field2.getAnnotationNames());
                }
            }
        }
        return cmp;
    }
}
