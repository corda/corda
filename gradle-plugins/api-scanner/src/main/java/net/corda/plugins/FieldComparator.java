package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import java.util.Comparator;

public class FieldComparator implements Comparator<FieldInfo> {

    @Override
    public int compare(FieldInfo field1, FieldInfo field2) {
        int cmp = field1.compareTo(field2);
        if (cmp == 0) {
            cmp = field1.getAccessFlags() - field2.getAccessFlags();
            if (cmp == 0) {
                String descriptor1 = field1.getTypeDescriptor();
                String descriptor2 = field2.getTypeDescriptor();
                cmp = descriptor1.compareTo(descriptor2);
                if (cmp == 0) {
                    cmp = new ListComparator<String>().compare(field1.getAnnotationNames(), field2.getAnnotationNames());
                }
            }
        }
        return cmp;
    }
}
