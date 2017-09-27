package net.corda.plugins;

import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.util.List;

public class ListComparatorTest {

    private List<String> list1;
    private List<String> list2;

    @Test
    public void twoEmptyLists() {
        list1 = emptyList();
        list2 = emptyList();
        assertEquals(0, new ListComparator<String>().compare(list1, list2));
    }

    @Test
    public void listsOfDifferentLengths() {
        list1 = asList("1", "2", "3");
        list2 = asList("100", "200");
        assertTrue(new ListComparator<String>().compare(list1, list2) > 0);
        assertTrue(new ListComparator<String>().compare(list2, list1) < 0);
    }

    @Test
    public void testDifferentListsOfEqualLength() {
        list1 = singletonList("1");
        list2 = singletonList("100");
        assertTrue(new ListComparator<String>().compare(list1, list2) < 0);
        assertTrue(new ListComparator<String>().compare(list2, list1) > 0);
    }

    @Test
    public void testEqualLists() {
        list1 = asList("11", "12", "13");
        list2 = asList("11", "12", "13");
        assertEquals(0, new ListComparator<String>().compare(list1, list2));
    }

    @Test
    public void testDifferAtLastElement() {
        list1 = asList("1", "2", "3");
        list2 = asList("1", "2", "300");
        assertTrue(new ListComparator<String>().compare(list1, list2) < 0);
        assertTrue(new ListComparator<String>().compare(list2, list1) > 0);
    }
}
