package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;
import java.util.Comparator;

public class MethodComparator implements Comparator<MethodInfo> {

    @Override
    public int compare(MethodInfo method1, MethodInfo method2) {
        int cmp = method1.compareTo(method2);
        if (cmp == 0) {
            cmp = method1.getAccessFlags() - method2.getAccessFlags();
            if (cmp == 0) {
                cmp = new ListComparator<String>().compare(method1.getAnnotationNames(), method2.getAnnotationNames());
            }
        }
        return cmp;
    }
}
