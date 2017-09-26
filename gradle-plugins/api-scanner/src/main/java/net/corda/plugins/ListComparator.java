package net.corda.plugins;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ListComparator<T extends Comparable<T>> implements Comparator<List<T>> {
    @Override
    public int compare(List<T> list1, List<T> list2) {
        int cmp = list1.size() - list2.size();
        if (cmp == 0) {
            Iterator<T> iter1 = list1.iterator();
            Iterator<T> iter2 = list2.iterator();
            while (iter1.hasNext()) {
                cmp = iter1.next().compareTo(iter2.next());
                if (cmp != 0) {
                    break;
                }
            }
        }
        return cmp;
    }
}
